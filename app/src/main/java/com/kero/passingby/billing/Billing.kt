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
    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                queryProducts()
                restore()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** 이미 산 상품을 다시 확인한다(구매 복원). */
    fun restore() {
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases)
        }
    }

    /** 결제 화면을 띄운다. 상품 정보가 아직 없으면(미등록·오프라인) false. */
    fun purchase(activity: Activity, productId: String): Boolean {
        val d = details[productId] ?: return false
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    fun end() {
        client.endConnection()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) handle(purchases)
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
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
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
                client.acknowledgePurchase(ack) { }
            }
        }
        main.post { onOwned(owned) }
    }
}
