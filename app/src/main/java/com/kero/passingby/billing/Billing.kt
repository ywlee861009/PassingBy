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

/**
 * 코스 결제(Play Billing, 일회성 상품). 상품이 Play Console에 등록되기 전에는 상품 정보를 못 받아 오므로
 * [purchase]가 false를 돌려주고, 화면은 "아직 구매할 수 없어요"를 보여 준다.
 *
 * 결제 라이브러리 콜백은 다른 스레드에서 올 수 있어 결과는 모두 메인 스레드로 넘긴다.
 *
 * @param onPrices 상품 ID → 표시 가격.
 * @param onOwned 구매 완료된 상품 ID 묶음. 구매 복원·새 구매 모두 이걸로 알린다.
 */
class Billing(
    context: Context,
    private val productIds: List<String>,
    private val onPrices: (Map<String, String>) -> Unit,
    private val onOwned: (Set<String>) -> Unit,
) : PurchasesUpdatedListener {
    private val main = Handler(Looper.getMainLooper())
    private val details = mutableMapOf<String, ProductDetails>()
    /** 마지막으로 결제 창을 띄운 상품. 결제 결과에는 실패 시 상품 정보가 없어 이걸로 남긴다. */
    @Volatile private var lastProduct = ""
    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    report("setup", result)
                    return
                }
                queryProducts()
                restore()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Play 결제에 연결돼 있는지. 연결 전이면 구매 복원이 아무 일도 하지 않는다. */
    val ready: Boolean
        get() = client.isReady

    /** 이미 산 상품을 다시 확인한다(구매 복원). */
    fun restore() {
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases) else report("purchases", result)
        }
    }

    /**
     * 결제 화면을 띄운다. 상품 정보가 아직 없으면(미등록·오프라인) false.
     * 일회성 상품은 구매 옵션의 오퍼 토큰을 함께 넘겨야 한다. 가격과 같은 "이전 버전과의 호환성" 구매 옵션을 쓰고,
     * 토큰이 없으면 예전처럼 토큰 없이 띄운다.
     */
    fun purchase(activity: Activity, productId: String): Boolean {
        val d = details[productId] ?: return false
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
        client.endConnection()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        Telemetry.event("purchase_result", "product" to lastProduct, "code" to result.responseCode)
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> if (purchases != null) handle(purchases)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> report("purchase", result)
        }
    }

    private fun queryProducts() {
        val products = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, r ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                report("products", result)
                return@queryProductDetailsAsync
            }
            // Play Console에서 비활성화했거나 ID가 다른 상품은 목록에서 빠진다. 그 코스는 살 수 없게 된다.
            val missing = productIds - r.productDetailsList.map { it.productId }.toSet()
            if (missing.isNotEmpty()) {
                Telemetry.event("billing_products_missing", "count" to missing.size, "ids" to missing.joinToString(",").take(100))
            }
            val prices = mutableMapOf<String, String>()
            for (d in r.productDetailsList) {
                details[d.productId] = d
                d.oneTimePurchaseOfferDetails?.formattedPrice?.let { prices[d.productId] = it }
            }
            main.post { onPrices(prices) }
        }
    }

    /** 결제 완료된 상품을 확인(acknowledge)하고 알린다. 확인하지 않으면 3일 뒤 자동 환불된다. */
    private fun handle(purchases: List<Purchase>) {
        val owned = mutableSetOf<String>()
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            owned += p.products
            if (!p.isAcknowledged) {
                val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                client.acknowledgePurchase(ack) { r ->
                    if (r.responseCode != BillingClient.BillingResponseCode.OK) report("ack", r)
                }
            }
        }
        main.post { onOwned(owned) }
    }

    /** 결제 단계의 실패를 남긴다. 개발 실수(DEVELOPER_ERROR)나 알 수 없는 오류(ERROR)는 오류 보고로도 올린다. */
    private fun report(stage: String, result: BillingResult) {
        val code = result.responseCode
        Telemetry.event("billing_error", "stage" to stage, "code" to code)
        if (code == BillingClient.BillingResponseCode.DEVELOPER_ERROR || code == BillingClient.BillingResponseCode.ERROR) {
            Telemetry.error(BillingFailure(stage, code, result.debugMessage), "billing_$stage")
        }
    }

    private class BillingFailure(stage: String, code: Int, message: String) : Exception("$stage: code=$code $message")
}
