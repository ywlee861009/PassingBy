package com.kero.passingby.billing

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
import com.android.billingclient.api.BillingClient.BillingResponseCode.DEVELOPER_ERROR
import com.android.billingclient.api.BillingClient.BillingResponseCode.NETWORK_ERROR
import com.android.billingclient.api.BillingClient.BillingResponseCode.OK
import com.android.billingclient.api.BillingClient.BillingResponseCode.USER_CANCELED
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Runs Billing with Android's main looper and a fake Play connection. */
class BillingFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun aFailedThenPartialCatalogQueryAutomaticallyRecoversTheMissingPrice() = withFlow {
        onMain { billing.connect() }
        play.nextProductRequest().apply {
            assertEquals(listOf(FIRST, SECOND), productIds)
            respond(NETWORK_ERROR)
        }
        play.nextProductRequest().apply {
            assertEquals(listOf(FIRST, SECOND), productIds)
            respond(OK, detail(FIRST, FIRST_PRICE, 900_000_000L))
        }
        play.nextProductRequest().apply {
            assertEquals(listOf(SECOND), productIds)
            onMain { assertEquals(mapOf(FIRST to FIRST_PRICE), prices) }
            respond(OK, detail(SECOND, SECOND_PRICE, 1_500_000_000L))
        }

        instrumentation.waitForIdleSync()
        onMain {
            assertEquals(mapOf(FIRST to FIRST_PRICE, SECOND to SECOND_PRICE), prices)
            assertEquals(PurchaseLaunch.LAUNCHED, purchase(SECOND))
            assertEquals(1, play.launches)
        }
    }

    @Test fun rapidTapsAcrossCoursesLaunchOnceAndApprovalGrantsAndAcknowledges() = withFlow {
        loadPrices()
        onMain {
            assertEquals(PurchaseLaunch.LAUNCHED, purchase(FIRST))
            repeat(5) {
                assertEquals(PurchaseLaunch.IN_PROGRESS, purchase(FIRST))
                assertEquals(PurchaseLaunch.IN_PROGRESS, purchase(SECOND))
            }
            assertEquals(1, play.launches)
            assertEquals(emptySet<String>(), owned)
            play.updatePurchase(OK, listOf(approvedPurchase(FIRST)))
        }

        instrumentation.waitForIdleSync()
        onMain {
            assertEquals(setOf(FIRST), owned)
            assertEquals(listOf(PURCHASE_TOKEN), play.acknowledged)
            assertEquals(0, purchaseFailures)
            assertEquals(PurchaseLaunch.LAUNCHED, purchase(SECOND))
            assertEquals(2, play.launches)
        }
    }

    @Test fun cancellationAndLaunchFailureReleaseCheckoutForAnotherAttempt() = withFlow {
        loadPrices()
        onMain {
            assertEquals(PurchaseLaunch.LAUNCHED, purchase(FIRST))
            play.updatePurchase(USER_CANCELED)
        }

        instrumentation.waitForIdleSync()
        onMain {
            play.nextLaunchCode = DEVELOPER_ERROR
            assertEquals(PurchaseLaunch.UNAVAILABLE, purchase(FIRST))
            assertEquals(PurchaseLaunch.IN_PROGRESS, purchase(SECOND))
            assertEquals(2, play.launches)
        }

        instrumentation.waitForIdleSync()
        onMain {
            assertEquals(emptySet<String>(), owned)
            assertEquals(emptyList<String>(), play.acknowledged)
            assertEquals(0, purchaseFailures)
            assertEquals(PurchaseLaunch.LAUNCHED, purchase(SECOND))
            assertEquals(3, play.launches)
            play.updatePurchase(DEVELOPER_ERROR)
        }

        instrumentation.waitForIdleSync()
        onMain {
            assertEquals(1, purchaseFailures)
            assertEquals(PurchaseLaunch.LAUNCHED, purchase(SECOND))
            assertEquals(4, play.launches)
        }
    }

    @Test fun aPermanentCatalogErrorDoesNotAutomaticallyRetryOrOpenCheckout() = withFlow {
        onMain { billing.connect() }
        play.nextProductRequest().respond(BILLING_UNAVAILABLE)

        assertNull(play.productRequests.poll(2, TimeUnit.SECONDS))
        onMain {
            assertEquals(emptyMap<String, String>(), prices)
            assertEquals(PurchaseLaunch.PLAY_UNAVAILABLE, purchase(FIRST))
            assertEquals(0, play.launches)
        }
        assertTrue(FIRST in play.nextProductRequest().productIds)
    }

    @Test fun transientCatalogFailuresStopAfterTheRetryBudget() = withFlow {
        onMain { billing.connect() }
        repeat(4) {
            play.nextProductRequest().apply {
                assertEquals(listOf(FIRST, SECOND), productIds)
                respond(NETWORK_ERROR)
            }
        }

        assertNull("Automatic retries should stop after the third retry", play.productRequests.poll(9, TimeUnit.SECONDS))
        onMain {
            assertEquals(PurchaseLaunch.UNAVAILABLE, purchase(FIRST))
            assertEquals(0, play.launches)
        }
        assertTrue(FIRST in play.nextProductRequest().productIds)
    }

    @Test fun returningWithoutACallbackAllowsCheckoutAgain() = withFlow {
        loadPrices()
        val returned = CountDownLatch(1)
        onMain {
            assertEquals(PurchaseLaunch.LAUNCHED, purchase(FIRST))
            billing.onPause()
            billing.onResume()
            assertEquals(PurchaseLaunch.IN_PROGRESS, purchase(SECOND))
            Handler(Looper.getMainLooper()).postDelayed({ returned.countDown() }, 2_100)
        }

        assertTrue(returned.await(6, TimeUnit.SECONDS))
        onMain {
            assertEquals(PurchaseLaunch.LAUNCHED, purchase(SECOND))
            assertEquals(2, play.launches)
            play.updatePurchase(USER_CANCELED)
            assertEquals(PurchaseLaunch.IN_PROGRESS, purchase(FIRST))
            assertEquals(2, play.launches)
        }
    }

    private fun Flow.loadPrices() {
        onMain { billing.connect() }
        play.nextProductRequest().respond(
            OK,
            detail(FIRST, FIRST_PRICE, 900_000_000L),
            detail(SECOND, SECOND_PRICE, 1_500_000_000L),
        )
        instrumentation.waitForIdleSync()
    }

    private fun withFlow(block: Flow.() -> Unit) {
        val flow = onMain { Flow() }
        try {
            flow.block()
        } finally {
            onMain { flow.billing.end() }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var outcome: Result<T>? = null
        instrumentation.runOnMainSync { outcome = runCatching(block) }
        return checkNotNull(outcome).getOrThrow()
    }

    private class Flow {
        private val activity = Activity()
        val play = FakePlay()
        var prices = emptyMap<String, String>()
            private set
        var owned = emptySet<String>()
            private set
        var purchaseFailures = 0
            private set
        val billing = Billing(
            productIds = listOf(FIRST, SECOND),
            onPrices = { prices = it },
            onOwned = { ids, _ -> owned += ids },
            onRestore = {},
            onPending = {},
            onPurchaseFailed = { purchaseFailures++ },
            createClient = { listener -> play.apply { purchasesUpdated = listener } },
        )

        fun purchase(id: String) = billing.purchase(activity, id)
    }

    private class FakePlay : PlayBillingConnection {
        private val main = Handler(Looper.getMainLooper())
        lateinit var purchasesUpdated: PurchasesUpdatedListener
        val productRequests = LinkedBlockingQueue<ProductRequest>()
        val acknowledged = mutableListOf<String>()
        var nextLaunchCode = OK
        var launches = 0
            private set
        override var isReady = false
            private set

        override fun startConnection(listener: BillingClientStateListener) {
            isReady = true
            listener.onBillingSetupFinished(result(OK))
        }

        override fun endConnection() {
            isReady = false
        }

        override fun queryPurchases(params: QueryPurchasesParams, listener: PurchasesResponseListener) {
            listener.onQueryPurchasesResponse(result(OK), emptyList())
        }

        override fun queryProducts(productIds: List<String>, callback: (BillingResult, List<ProductDetails>) -> Unit) {
            productRequests.add(ProductRequest(productIds.toList(), callback))
        }

        override fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult {
            launches++
            val response = result(nextLaunchCode)
            nextLaunchCode = OK
            if (response.responseCode != OK) {
                // Play Billing also delivers immediate launch rejections to the listener.
                main.post { purchasesUpdated.onPurchasesUpdated(response, null) }
            }
            return response
        }

        override fun acknowledgePurchase(params: AcknowledgePurchaseParams, listener: AcknowledgePurchaseResponseListener) {
            acknowledged += params.purchaseToken
            listener.onAcknowledgePurchaseResponse(result(OK))
        }

        fun nextProductRequest(): ProductRequest = checkNotNull(productRequests.poll(8, TimeUnit.SECONDS)) {
            "No product details request arrived"
        }

        fun updatePurchase(code: Int, purchases: List<Purchase>? = null) {
            purchasesUpdated.onPurchasesUpdated(result(code), purchases)
        }
    }

    private class ProductRequest(
        val productIds: List<String>,
        private val callback: (BillingResult, List<ProductDetails>) -> Unit,
    ) {
        fun respond(code: Int, vararg details: ProductDetails) {
            callback(result(code), details.toList())
        }
    }

    private companion object {
        const val FIRST = "course_korea_gangneung"
        const val SECOND = "course_portugal"
        const val FIRST_PRICE = "₩900"
        const val SECOND_PRICE = "₩1,500"
        const val PURCHASE_TOKEN = "instrumentation-purchase-token"

        fun result(code: Int): BillingResult = BillingResult.newBuilder().setResponseCode(code).build()

        fun detail(id: String, price: String, priceMicros: Long): ProductDetails {
            val json = JSONObject()
                .put("productId", id)
                .put("type", "inapp")
                .put("skuDetailsToken", "$id-details")
                .put("oneTimePurchaseOfferDetails", JSONObject()
                    .put("formattedPrice", price)
                    .put("priceAmountMicros", priceMicros)
                    .put("priceCurrencyCode", "KRW")
                    .put("offerIdToken", "$id-offer"))
            // The SDK has no public ProductDetails builder.
            return ProductDetails::class.java.getDeclaredConstructor(String::class.java)
                .apply { isAccessible = true }
                .newInstance(json.toString())
        }

        fun approvedPurchase(id: String): Purchase = Purchase(
            JSONObject()
                .put("productIds", JSONArray(listOf(id)))
                .put("purchaseState", 0)
                .put("acknowledged", false)
                .put("purchaseToken", PURCHASE_TOKEN)
                .toString(),
            "instrumentation-signature",
        )
    }
}
