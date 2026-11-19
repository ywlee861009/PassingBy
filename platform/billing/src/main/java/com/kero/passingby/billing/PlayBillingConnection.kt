package com.kero.passingby.billing

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

internal interface PlayBillingConnection {
    val isReady: Boolean
    val playStoreAvailability: String get() = "unknown"
    fun startConnection(listener: BillingClientStateListener)
    fun endConnection()
    fun queryPurchases(params: QueryPurchasesParams, listener: PurchasesResponseListener)
    fun queryProducts(productIds: List<String>, callback: (BillingResult, List<ProductDetails>) -> Unit)
    fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult
    fun acknowledgePurchase(params: AcknowledgePurchaseParams, listener: AcknowledgePurchaseResponseListener)
}

internal fun playBillingFactory(context: Context): (PurchasesUpdatedListener) -> PlayBillingConnection {
    val appContext = context.applicationContext
    return { listener -> GooglePlayBillingConnection(appContext, listener) }
}

private class GooglePlayBillingConnection(
    private val context: Context,
    listener: PurchasesUpdatedListener,
) : PlayBillingConnection {
    private val client = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override val isReady: Boolean get() = client.isReady
    override val playStoreAvailability: String
        get() = try {
            @Suppress("DEPRECATION")
            val store = context.packageManager.getApplicationInfo("com.android.vending", 0)
            if (store.enabled) "enabled" else "disabled"
        } catch (_: PackageManager.NameNotFoundException) {
            "missing"
        }

    override fun startConnection(listener: BillingClientStateListener) = client.startConnection(listener)
    override fun endConnection() = client.endConnection()
    override fun queryPurchases(params: QueryPurchasesParams, listener: PurchasesResponseListener) =
        client.queryPurchasesAsync(params, listener)

    override fun queryProducts(productIds: List<String>, callback: (BillingResult, List<ProductDetails>) -> Unit) {
        val products = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        client.queryProductDetailsAsync(params) { result, response -> callback(result, response.productDetailsList) }
    }

    override fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult =
        client.launchBillingFlow(activity, params)

    override fun acknowledgePurchase(params: AcknowledgePurchaseParams, listener: AcknowledgePurchaseResponseListener) =
        client.acknowledgePurchase(params, listener)
}
