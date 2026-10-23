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
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.kero.passingby.telemetry.Telemetry

/** 일회성 코스 구매. 모든 상태와 화면 콜백은 메인 스레드에서 처리한다. */
class Billing(
    context: Context,
    private val productIds: List<String>,
    private val onPrices: (Map<String, String>) -> Unit,
    private val onOwned: (Set<String>, PurchaseSource) -> Unit,
    private val onRestore: (RestoreStatus) -> Unit,
    private val onPending: () -> Unit,
    private val onPurchaseFailed: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val appContext = context.applicationContext
    private val known = productIds.toSet()
    private val refresh = PurchaseRefresh()
    private val details = mutableMapOf<String, ProductDetails>()
    private val acknowledging = mutableSetOf<String>()
    private val pendingProducts = mutableSetOf<String>()
    private var lastProduct = ""
    private var closed = false
    private var connectedOnce = false
    private var deadline: Runnable? = null
    private val listener = PurchasesUpdatedListener { result, purchases -> onPurchasesUpdated(result, purchases) }
    private var client = newClient()

    private fun newClient(): BillingClient = BillingClient.newBuilder(appContext)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun connect() = refreshPurchases(manual = false)
    val ready: Boolean get() = client.isReady
    fun restore() = refreshPurchases(manual = true)
    fun onResume() = refreshPurchases(manual = false)

    private fun refreshPurchases(manual: Boolean) {
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
                client = newClient()
                connectedOnce = false
            }
            finish(id, false)
        }.also { main.postDelayed(it, 15_000L) }
        if (request.connect) startConnection(id) else queryPurchases(id)
    }

    private fun startConnection(id: Long) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                main.post {
                    if (closed || !refresh.connected(id)) return@post
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        report("setup", result)
                        finish(id, false)
                    } else {
                        connectedOnce = true
                        queryProducts()
                        queryPurchases(id)
                    }
                }
            }
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun queryPurchases(id: Long) {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
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
        if (details.isEmpty()) queryProducts()
    }

    private fun finish(id: Long, success: Boolean, hasPurchases: Boolean = false, hasPending: Boolean = false) {
        deadline?.let(main::removeCallbacks)
        deadline = null
        refresh.finish(id, success, hasPurchases, hasPending)?.let(onRestore)
    }

    fun purchase(activity: Activity, productId: String): Boolean {
        if (closed) return false
        val d = details[productId] ?: run { refreshPurchases(false); return false }
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(d)
            .apply { d.oneTimePurchaseOfferDetails?.offerToken?.let { setOfferToken(it) } }
            .build()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()
        lastProduct = productId
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) report("launch", result)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun end() {
        closed = true
        refresh.cancel()
        main.removeCallbacksAndMessages(null)
        client.endConnection()
        acknowledging.clear()
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        main.post {
            if (closed) return@post
            Telemetry.event("purchase_result", "product" to lastProduct, "code" to result.responseCode)
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> if (purchases != null) handle(purchases, PurchaseSource.UPDATE)
                BillingClient.BillingResponseCode.USER_CANCELED -> Unit
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
                else -> { report("purchase", result); onPurchaseFailed() }
            }
        }
    }

    private var productsInFlight = false
    private var productsRequest = 0L
    private fun queryProducts() {
        if (productsInFlight || closed) return
        productsInFlight = true
        val request = ++productsRequest
        val timeout = Runnable { if (request == productsRequest) { productsInFlight = false; productsRequest++ } }
        main.postDelayed(timeout, 15_000L)
        val products = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, response ->
            main.post {
                if (closed || request != productsRequest) return@post
                main.removeCallbacks(timeout)
                productsInFlight = false
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    report("products", result)
                    return@post
                }
                val missing = productIds - response.productDetailsList.map { it.productId }.toSet()
                if (missing.isNotEmpty()) Telemetry.event("billing_products_missing", "count" to missing.size, "ids" to missing.joinToString(",").take(100))
                details.clear()
                val prices = mutableMapOf<String, String>()
                for (d in response.productDetailsList) {
                    details[d.productId] = d
                    d.oneTimePurchaseOfferDetails?.formattedPrice?.let { prices[d.productId] = it }
                }
                onPrices(prices)
            }
        }
    }

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

    private fun report(stage: String, result: BillingResult) {
        val code = result.responseCode
        Telemetry.event("billing_error", "stage" to stage, "code" to code)
        if (code == BillingClient.BillingResponseCode.DEVELOPER_ERROR || code == BillingClient.BillingResponseCode.ERROR) {
            // Store debugMessage는 거래 식별자를 포함할 수 있으므로 보고에 넣지 않는다.
            Telemetry.error(BillingFailure(stage, code), "billing_$stage")
        }
    }

    private class BillingFailure(stage: String, code: Int) : Exception("$stage: code=$code")
}
