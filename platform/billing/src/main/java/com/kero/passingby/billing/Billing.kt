package com.kero.passingby.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UnfetchedProduct
import com.kero.passingby.telemetry.Telemetry

enum class PurchaseLaunch { LAUNCHED, IN_PROGRESS, CHECKING_PRICE, UNAVAILABLE, PLAY_UNAVAILABLE }
enum class CheckoutPriceCheckStatus { CHECKING, READY, RETRY_NEEDED, PLAY_UNAVAILABLE, CANCELED }

/** 일회성 코스 구매. 모든 상태와 화면 콜백은 메인 스레드에서 처리한다. */
class Billing internal constructor(
    productIds: List<String>,
    private val onPrices: (Map<String, String>) -> Unit,
    private val onOwned: (Set<String>, PurchaseSource) -> Unit,
    private val onRestore: (RestoreStatus) -> Unit,
    private val onPending: () -> Unit,
    private val onPurchaseFailed: () -> Unit,
    private val createClient: (PurchasesUpdatedListener) -> PlayBillingConnection,
    private val onCheckoutPriceCheck: (String, CheckoutPriceCheckStatus) -> Unit = { _, _ -> },
) {
    constructor(
        context: Context,
        productIds: List<String>,
        onPrices: (Map<String, String>) -> Unit,
        onOwned: (Set<String>, PurchaseSource) -> Unit,
        onRestore: (RestoreStatus) -> Unit,
        onPending: () -> Unit,
        onPurchaseFailed: () -> Unit,
        onCheckoutPriceCheck: (String, CheckoutPriceCheckStatus) -> Unit = { _, _ -> },
    ) : this(productIds, onPrices, onOwned, onRestore, onPending, onPurchaseFailed, playBillingFactory(context), onCheckoutPriceCheck)

    private class Checkout(val productId: String) {
        var rejection: BillingResult? = null
        var hostPaused = false
        var hasPaused = false
    }

    private data class CatalogFailure(val reason: String, val code: Int? = null, val products: Set<String>? = null)

    private val main = Handler(Looper.getMainLooper())
    private val known = productIds.toSet()
    private val refresh = PurchaseRefresh()
    private val details = mutableMapOf<String, ProductDetails>()
    private val unfetchedProducts = mutableMapOf<String, Int>()
    private val acknowledging = mutableSetOf<String>()
    private val pendingProducts = mutableSetOf<String>()
    private var checkout: Checkout? = null
    private var checkoutWithoutResult: Checkout? = null
    private var checkoutReturn: Runnable? = null
    private val checkoutLaunchDeadline = Runnable {
        val active = checkout ?: return@Runnable
        checkoutWithoutResult = active
        clearCheckout()
        Telemetry.event("billing_checkout_no_result", "product" to active.productId, "reason" to "no_play_window")
        onPurchaseFailed()
    }
    private var checkoutPriceProduct: String? = null
    private var checkoutPriceRetries = 0
    private val checkoutPriceDeadline = Runnable {
        finishCheckoutPriceCheck(CheckoutPriceCheckStatus.RETRY_NEEDED, "timeout")
    }
    private var productsUnavailable = false
    private var lastCatalogFailure: CatalogFailure? = null
    private var productRequest = 0L
    private var productDeadline: Runnable? = null
    private var productRetry: Runnable? = null
    private var closed = false
    private var connectedOnce = false
    private var deadline: Runnable? = null
    private val listener = PurchasesUpdatedListener { result, purchases ->
        if (Looper.myLooper() == main.looper) onPurchasesUpdated(result, purchases)
        else main.post { onPurchasesUpdated(result, purchases) }
    }
    private var client = createClient(listener)

    fun connect() = refreshPurchases(manual = false)
    val ready: Boolean get() = client.isReady
    fun restore() = refreshPurchases(manual = true)
    fun onPause() {
        cancelCheckoutPriceCheck()
        main.removeCallbacks(checkoutLaunchDeadline)
        checkoutReturn?.let(main::removeCallbacks)
        checkoutReturn = null
        checkout?.let { active ->
            active.hostPaused = true
            active.hasPaused = true
        }
    }

    fun onResume() {
        val active = checkout
        if (active?.hostPaused == true) {
            active.hostPaused = false
            // Play 결과 콜백이 유실된 경우, 결제창에서 복귀한 뒤에만 새 요청을 허용한다.
            checkoutReturn = Runnable {
                checkoutReturn = null
                if (checkout === active) {
                    checkoutWithoutResult = active
                    checkout = null
                }
            }.also { main.postDelayed(it, 2_000L) }
        }
        refreshPurchases(manual = false)
    }

    private fun refreshPurchases(manual: Boolean, productAttempt: Int = 0) {
        if (closed) return
        val wasRestoring = refresh.restoring
        // 연결을 한 번 마친 뒤의 끊김은 query API의 자동 재연결에 맡긴다.
        val request = refresh.begin(manual, client.isReady || connectedOnce)
        if (manual && !wasRestoring) onRestore(RestoreStatus.CHECKING)
        if (request == null) return
        Telemetry.event("billing_query", "stage" to if (request.connect) "connect" else "query", "manual" to manual)
        val id = request.id
        deadline = Runnable {
            val connecting = refresh.connecting
            Telemetry.event("billing_query", "stage" to "timeout", "manual" to refresh.restoring)
            if (connecting) {
                client.endConnection()
                client = createClient(listener)
                connectedOnce = false
                lastCatalogFailure = CatalogFailure("connection_timeout")
            }
            finish(id, false)
            if (connecting) scheduleProductRetry(productAttempt)
        }.also { main.postDelayed(it, 15_000L) }
        if (request.connect) startConnection(id, productAttempt) else queryPurchases(id, productAttempt)
    }

    private fun startConnection(id: Long, productAttempt: Int) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                main.post {
                    if (closed || !refresh.connected(id)) return@post
                    val code = result.responseCode
                    if (code != BillingClient.BillingResponseCode.OK) {
                        productsUnavailable = report("setup", result)
                        lastCatalogFailure = CatalogFailure("connection_error", code)
                        finish(id, false)
                        if (!productsUnavailable && canRetryProducts(code)) scheduleProductRetry(productAttempt)
                        else finishCheckoutPriceCheckFailure()
                    } else {
                        productsUnavailable = false
                        connectedOnce = true
                        queryPurchases(id, productAttempt)
                    }
                }
            }
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun queryPurchases(id: Long, productAttempt: Int) {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchases(params) { result, purchases ->
            main.post {
                if (closed || !refresh.querying(id)) return@post
                Telemetry.event("billing_query", "stage" to "result", "code" to result.responseCode, "manual" to refresh.restoring)
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val batch = handle(purchases, PurchaseSource.QUERY)
                    finish(id, true, batch.owned.isNotEmpty(), batch.pending.isNotEmpty())
                } else {
                    report("purchases", result)
                    finish(id, false)
                }
            }
        }
        queryProducts(productAttempt)
    }

    private fun finish(id: Long, success: Boolean, hasPurchases: Boolean = false, hasPending: Boolean = false) {
        deadline?.let(main::removeCallbacks)
        deadline = null
        refresh.finish(id, success, hasPurchases, hasPending)?.let(onRestore)
    }

    fun checkPrice(productId: String) {
        if (closed || productId !in known || productId in details) return
        startCheckoutPriceCheck(productId)
    }

    fun purchase(activity: Activity, productId: String): PurchaseLaunch {
        if (closed || productId !in known) return PurchaseLaunch.UNAVAILABLE
        if (checkout != null) {
            Telemetry.event("billing_checkout_blocked", "product" to productId, "reason" to "in_progress")
            return PurchaseLaunch.IN_PROGRESS
        }
        val d = details[productId]
        if (d == null) {
            if (checkoutPriceProduct == productId) return PurchaseLaunch.CHECKING_PRICE
            Telemetry.event(
                "billing_checkout_blocked", "product" to productId,
                "reason" to if (productsUnavailable) "play_unavailable" else "no_product_details",
                *catalogDiagnostics(productId, catalogReason(productId)),
            )
            return startCheckoutPriceCheck(productId)
        }
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(d)
            .apply { d.oneTimePurchaseOfferDetails?.offerToken?.let { setOfferToken(it) } }
            .build()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()
        val active = Checkout(productId)
        checkout = active
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            if (checkout === active && !active.hasPaused) main.postDelayed(checkoutLaunchDeadline, 10_000L)
            Telemetry.event("billing_checkout_opened", "product" to productId)
            return PurchaseLaunch.LAUNCHED
        }
        // Play Billing can echo this same rejected result on the main queue.
        active.rejection = result
        main.post { if (checkout === active) clearCheckout() }
        val playUnavailable = report("launch", result)
        Telemetry.event("billing_checkout_failed", "product" to productId, "stage" to "launch", "code" to result.responseCode)
        if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) restore()
        return if (playUnavailable) PurchaseLaunch.PLAY_UNAVAILABLE else PurchaseLaunch.UNAVAILABLE
    }

    private fun clearCheckout() {
        checkout = null
        main.removeCallbacks(checkoutLaunchDeadline)
        checkoutReturn?.let(main::removeCallbacks)
        checkoutReturn = null
    }

    fun end() {
        closed = true
        refresh.cancel()
        cancelCheckoutPriceCheck()
        main.removeCallbacksAndMessages(null)
        clearCheckout()
        checkoutWithoutResult = null
        client.endConnection()
        acknowledging.clear()
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val active = checkout
        if (closed || active?.rejection === result) return
        val code = result.responseCode
        val productIds = purchases.orEmpty().flatMap { it.products }
        val previous = checkoutWithoutResult
        // A result without a product ID can arrive after the host returned to the app.
        val lateResult = previous != null && (productIds.isEmpty() || previous.productId in productIds) &&
            (active?.hasPaused != true || productIds.isNotEmpty() && active.productId !in productIds)
        if (lateResult) checkoutWithoutResult = null
        val resultCheckout = if (lateResult) previous else active?.takeIf {
            it.rejection == null && (productIds.isEmpty() || it.productId in productIds)
        }
        val product = resultCheckout?.productId ?: productIds.firstOrNull().orEmpty()
        if (resultCheckout != null && resultCheckout === active) clearCheckout()
        Telemetry.event("purchase_result", "product" to product, "code" to code)
        when (code) {
            BillingClient.BillingResponseCode.OK -> if (purchases != null) {
                val batch = handle(purchases, PurchaseSource.UPDATE)
                if (resultCheckout?.productId in batch.owned) Telemetry.event("billing_checkout_completed", "product" to product)
                if (resultCheckout?.productId in batch.pending) Telemetry.event("billing_checkout_pending", "product" to product)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> if (resultCheckout != null) {
                Telemetry.event("billing_checkout_canceled", "product" to product)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            else -> {
                report("purchase", result)
                if (resultCheckout != null) {
                    Telemetry.event("billing_checkout_failed", "product" to product, "stage" to "result", "code" to code)
                }
                if (!lateResult || active == null) onPurchaseFailed()
            }
        }
    }

    private fun requestProducts(attempt: Int = 0) {
        if (closed || productDeadline != null || productRetry != null || details.keys.containsAll(known)) return
        if (client.isReady || connectedOnce) queryProducts(attempt)
        else refreshPurchases(manual = false, productAttempt = attempt)
    }

    private fun startCheckoutPriceCheck(productId: String): PurchaseLaunch {
        if (checkoutPriceProduct == productId) return PurchaseLaunch.CHECKING_PRICE
        cancelCheckoutPriceCheck()
        checkoutPriceProduct = productId
        checkoutPriceRetries = 0
        val launch = if (productsUnavailable) {
            finishCheckoutPriceCheck(CheckoutPriceCheckStatus.PLAY_UNAVAILABLE)
            PurchaseLaunch.PLAY_UNAVAILABLE
        } else {
            main.postDelayed(checkoutPriceDeadline, 12_000L)
            onCheckoutPriceCheck(productId, CheckoutPriceCheckStatus.CHECKING)
            PurchaseLaunch.CHECKING_PRICE
        }
        // A user asking for a price takes priority over the next scheduled background retry.
        productRetry?.let(main::removeCallbacks)
        productRetry = null
        requestProducts()
        return launch
    }

    fun cancelCheckoutPriceCheck() = finishCheckoutPriceCheck(CheckoutPriceCheckStatus.CANCELED)

    private fun finishCheckoutPriceCheckFailure() = finishCheckoutPriceCheck(
        if (productsUnavailable) CheckoutPriceCheckStatus.PLAY_UNAVAILABLE else CheckoutPriceCheckStatus.RETRY_NEEDED,
    )

    private fun finishCheckoutPriceCheck(status: CheckoutPriceCheckStatus, reason: String? = null) {
        val productId = checkoutPriceProduct ?: return
        checkoutPriceProduct = null
        main.removeCallbacks(checkoutPriceDeadline)
        Telemetry.event(
            "billing_checkout_price_check", "product" to productId, "outcome" to status.name.lowercase(),
            *catalogDiagnostics(productId, reason ?: if (status == CheckoutPriceCheckStatus.READY) "available" else catalogReason(productId)),
        )
        onCheckoutPriceCheck(productId, status)
    }

    private fun catalogReason(productId: String): String = when (unfetchedProducts[productId]) {
        UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT -> "invalid_product_id"
        UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND -> "product_not_found"
        UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER -> "no_eligible_offer"
        UnfetchedProduct.StatusCode.UNKNOWN -> "product_details_missing"
        else -> catalogFailureFor(productId)?.reason ?: if (productDeadline != null || refresh.connecting) "query_in_progress" else "not_loaded"
    }

    private fun catalogFailureFor(productId: String): CatalogFailure? =
        lastCatalogFailure?.takeIf { it.products == null || productId in it.products }

    private fun catalogDiagnostics(productId: String, reason: String): Array<Pair<String, Any>> =
        buildList<Pair<String, Any>> {
            add("catalog_reason" to reason)
            catalogFailureFor(productId)?.code?.let { add("catalog_code" to it) }
            unfetchedProducts[productId]?.let { add("product_status" to it) }
        }.toTypedArray()

    private fun queryProducts(attempt: Int) {
        if (closed || productDeadline != null || productRetry != null) return
        val missing = known - details.keys
        if (missing.isEmpty()) return
        val prioritized = checkoutPriceProduct?.takeIf { it in missing }
        val requested = prioritized?.let(::setOf) ?: missing
        val request = ++productRequest
        productDeadline = Runnable {
            if (!finishProductQuery(request)) return@Runnable
            unfetchedProducts.keys.removeAll(requested)
            lastCatalogFailure = CatalogFailure("query_timeout", products = requested)
            Telemetry.event("billing_catalog_failed", "reason" to "timeout", "attempt" to attempt)
            scheduleProductRetry(attempt)
        }.also { main.postDelayed(it, 15_000L) }
        Telemetry.event("billing_catalog_query", "attempt" to attempt, "requested" to requested.size,
            "trigger" to if (prioritized != null) "checkout" else "background")
        client.queryProducts(requested.toList()) { result, response, unfetchedStatus ->
            main.post {
                if (!finishProductQuery(request)) return@post
                val code = result.responseCode
                if (code != BillingClient.BillingResponseCode.OK) {
                    unfetchedProducts.keys.removeAll(requested)
                    productsUnavailable = report("products", result)
                    lastCatalogFailure = CatalogFailure("query_error", code, requested)
                    Telemetry.event("billing_catalog_failed", "reason" to "play_error", "code" to code, "attempt" to attempt)
                    if (!productsUnavailable && canRetryProducts(code)) scheduleProductRetry(attempt)
                    else finishCheckoutPriceCheckFailure()
                    return@post
                }
                productsUnavailable = false
                lastCatalogFailure = null
                for (product in response) {
                    if (product.productId in requested && product.oneTimePurchaseOfferDetails != null) {
                        details[product.productId] = product
                    }
                }
                val unfetched = requested - details.keys
                for (id in requested) {
                    if (id in unfetched) unfetchedProducts[id] = unfetchedStatus[id] ?: UnfetchedProduct.StatusCode.UNKNOWN
                    else unfetchedProducts.remove(id)
                }
                val prices = details.values.mapNotNull { product ->
                    product.oneTimePurchaseOfferDetails?.formattedPrice?.let { product.productId to it }
                }.toMap()
                Telemetry.event("billing_catalog_result", "available" to prices.size, "missing" to known.size - prices.size,
                    "attempt" to attempt, "requested" to requested.size)
                onPrices(prices)
                if (unfetched.isNotEmpty()) {
                    Telemetry.event("billing_products_missing", "count" to unfetched.size, "ids" to unfetched.joinToString(",").take(100))
                }
                checkoutPriceProduct?.let { checking ->
                    when {
                        checking in prices -> finishCheckoutPriceCheck(CheckoutPriceCheckStatus.READY)
                        unfetchedProducts[checking] == UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT ||
                            unfetchedProducts[checking] == UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER -> finishCheckoutPriceCheckFailure()
                        else -> Unit
                    }
                }
                val checkingAnotherProduct = checkoutPriceProduct?.let { it !in requested } == true
                if (checkingAnotherProduct || (prioritized != null && unfetched.isEmpty())) {
                    requestProducts()
                } else if (unfetched.isNotEmpty()) {
                    scheduleProductRetry(attempt)
                }
            }
        }
    }

    private fun finishProductQuery(request: Long): Boolean {
        val timeout = productDeadline ?: return false
        if (closed || request != productRequest) return false
        main.removeCallbacks(timeout)
        productDeadline = null
        return true
    }

    private fun scheduleProductRetry(attempt: Int) {
        if (closed || productRetry != null || details.keys.containsAll(known)) return
        val retryAttempt = if (checkoutPriceProduct != null) checkoutPriceRetries else attempt
        if (retryAttempt >= 3) {
            finishCheckoutPriceCheckFailure()
            return
        }
        if (checkoutPriceProduct != null) checkoutPriceRetries++
        Telemetry.event("billing_catalog_retry", "attempt" to retryAttempt + 1)
        productRetry = Runnable {
            productRetry = null
            requestProducts(retryAttempt + 1)
        }.also { main.postDelayed(it, 1_000L shl retryAttempt) }
    }

    private fun canRetryProducts(code: Int): Boolean = when (code) {
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.ERROR -> true
        else -> false
    }

    private fun playUnavailable(code: Int) = code == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ||
        code == BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED

    private fun handle(purchases: List<Purchase>, source: PurchaseSource): PurchaseBatch {
        val batch = classifyPurchases(purchases.map { p ->
            PurchaseRecord(p.products, when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> PaymentState.PURCHASED
                Purchase.PurchaseState.PENDING -> PaymentState.PENDING
                else -> PaymentState.OTHER
            }, p.isAcknowledged)
        }, known)
        // QUERY는 전체 스냅샷, UPDATE는 거래 일부. 대기 중인 다른 상품을 부분 이벤트로 지우지 않는다.
        val newPending = batch.pending - pendingProducts
        if (source == PurchaseSource.QUERY) pendingProducts.clear()
        pendingProducts.removeAll(batch.owned)
        pendingProducts.addAll(batch.pending)
        onOwned(batch.owned, source)
        if (newPending.isNotEmpty()) {
            Telemetry.event("purchase_pending", "source" to source.name.lowercase(), "count" to newPending.size)
            if (!refresh.restoring || source == PurchaseSource.UPDATE) onPending()
        }
        for (index in batch.acknowledgeAt) acknowledge(purchases[index].purchaseToken)
        return batch
    }

    /** 토큰은 내부에서만 보관한다. 실패하면 최대 3회 재시도하고 다음 조회에서도 다시 확인한다. */
    private fun acknowledge(token: String, attempt: Int = 0) {
        if (closed || (attempt == 0 && !acknowledging.add(token))) return
        val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        client.acknowledgePurchase(ack) { result ->
            main.post {
                if (closed) return@post
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    acknowledging.remove(token)
                } else {
                    report("ack", result)
                    if (attempt < 3) main.postDelayed({ acknowledge(token, attempt + 1) }, 1_000L shl attempt)
                    else acknowledging.remove(token)
                }
            }
        }
    }

    private fun report(stage: String, result: BillingResult): Boolean {
        val code = result.responseCode
        val store = client.playStoreAvailability
        val unavailable = playUnavailable(code) || store == "missing" || store == "disabled"
        Telemetry.event("billing_error", "stage" to stage, "code" to code, "play_store" to store)
        if (unavailable) {
            Telemetry.event("billing_play_unavailable", "stage" to stage, "code" to code, "play_store" to store)
        }
        if (code == BillingClient.BillingResponseCode.DEVELOPER_ERROR || code == BillingClient.BillingResponseCode.ERROR) {
            // Store debugMessage는 거래 식별자를 포함할 수 있으므로 보고에 넣지 않는다.
            Telemetry.error(BillingFailure(stage, code), "billing_$stage")
        }
        return unavailable
    }

    private class BillingFailure(stage: String, code: Int) : Exception("$stage: code=$code")
}
