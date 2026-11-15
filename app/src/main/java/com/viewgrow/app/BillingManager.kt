package com.viewgrow.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import org.json.JSONObject

// Google Play Billing for the contact packs.
//
// The three packs are one-time products in the Play Console that can be bought again and again
// (consumable). Their product IDs are in PRODUCT_IDS below and must match the Play Console exactly.
// The price on screen is always the one Google Play sends (already in the member's currency,
// with tax), never a number typed into the app.
//
// After a payment the app does NOT raise the limit itself. It sends the purchase token to the
// database function web_verify_purchase, which checks it with Google. Only when the database says
// ok is the purchase consumed (so the pack can be bought again). If the check fails or there is no
// network, the purchase stays open and is tried again the next time the app opens.
object BillingManager {

    // Pack size -> Play Console product ID.
    val PRODUCT_IDS = mapOf(
        1000 to "contacts_1000",
        3000 to "contacts_3000",
        5000 to "contacts_5000"
    )

    private var client: BillingClient? = null
    private var appContext: Context? = null
    private val details = HashMap<String, ProductDetails>()
    private val inFlight = HashSet<String>()
    private var onLimitRaised: (() -> Unit)? = null
    private var onMessage: ((String) -> Unit)? = null

    // Screens that want to hear about results (a toast, a refresh of Home).
    fun listen(messages: (String) -> Unit, limitRaised: () -> Unit) {
        onMessage = messages
        onLimitRaised = limitRaised
    }

    // Called when the screen goes away, so the app does not hold on to it.
    fun stopListening() {
        onMessage = null
        onLimitRaised = null
    }

    private val updates = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handle(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            else -> onMessage?.invoke("The payment did not go through. You were not charged.")
        }
    }

    // Connects once and keeps the connection. whenReady runs as soon as Google Play can be asked.
    private fun connect(ctx: Context, whenReady: (BillingClient) -> Unit, failed: () -> Unit) {
        val app = ctx.applicationContext
        appContext = app
        val existing = client
        if (existing != null && existing.isReady) { whenReady(existing); return }
        val c = existing ?: BillingClient.newBuilder(app)
            .setListener(updates)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) whenReady(c) else failed()
            }
            override fun onBillingServiceDisconnected() {
                // Connects again the next time it is needed.
            }
        })
    }

    // Asks Google Play for the three packs. done gets pack size -> price text (empty map if Play is not available).
    fun loadPrices(ctx: Context, done: (Map<Int, String>) -> Unit) {
        connect(ctx, { c ->
            val products = PRODUCT_IDS.values.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
            c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { r, list ->
                val out = HashMap<Int, String>()
                if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (d in list) {
                        details[d.productId] = d
                        val price = d.oneTimePurchaseOfferDetails?.formattedPrice ?: continue
                        val size = PRODUCT_IDS.entries.firstOrNull { it.value == d.productId }?.key ?: continue
                        out[size] = price
                    }
                }
                done(out)
            }
        }, { done(emptyMap()) })
    }

    // Opens Google Play's own payment sheet for the pack.
    fun buy(activity: Activity, packSize: Int) {
        val id = PRODUCT_IDS[packSize] ?: return
        val d = details[id]
        val c = client
        if (d == null || c == null || !c.isReady) {
            onMessage?.invoke("Google Play is not available right now. Try again in a moment.")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())
            )
            .build()
        val r = c.launchBillingFlow(activity, params)
        if (r.responseCode != BillingClient.BillingResponseCode.OK && r.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            onMessage?.invoke("Could not open Google Play. Try again in a moment.")
        }
    }

    // Looks for purchases that were paid but not yet confirmed with the database (the app closed, no
    // network, the check failed). Called when the app opens, so a paid pack is never lost.
    fun retryOpenPurchases(ctx: Context) {
        if (!Store.loggedIn) return
        connect(ctx, { c ->
            c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
                if (r.responseCode == BillingClient.BillingResponseCode.OK) list.forEach { handle(it) }
            }
        }, {})
    }

    private fun handle(p: Purchase) {
        when (p.purchaseState) {
            Purchase.PurchaseState.PENDING ->
                onMessage?.invoke("Your payment is pending. Your limit goes up once Google Play confirms it.")
            Purchase.PurchaseState.PURCHASED -> verify(p)
            else -> {}
        }
    }

    // Sends the purchase to the database. Only an ok answer lets the purchase be consumed.
    private fun verify(p: Purchase) {
        val token = p.purchaseToken
        if (!Store.loggedIn || !inFlight.add(token)) return
        val args = JSONObject()
            .put("p_phone", Store.phone)
            .put("p_pin", Store.password)
            .put("p_product_id", p.products.firstOrNull() ?: "")
            .put("p_purchase_token", token)
            .put("p_order_id", p.orderId ?: "")
        Api.call("web_verify_purchase", args) { r ->
            inFlight.remove(token)
            val j = r.json
            if (j != null && j.optBoolean("ok")) {
                consume(token)
                Status.last = null
                onMessage?.invoke("Payment received. Your contact limit has gone up.")
                onLimitRaised?.invoke()
            } else if (j == null) {
                onMessage?.invoke("We could not confirm your payment yet. It will be checked again when you open the app.")
            } else {
                onMessage?.invoke("We could not confirm your payment. Message the admin with your Google Play order number.")
            }
        }
    }

    private fun consume(token: String) {
        client?.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build()) { _, _ -> }
    }
}
