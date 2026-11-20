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

    // Prices are fetched when the app opens and kept for the session, so the pop-up opens with them already in place.
    // NOT_AVAILABLE = Google Play answered but has no such products for this install (not installed from Google Play,
    // or the product IDs are not active in the Play Console): waiting or asking again will not help.
    // TRY_AGAIN = no answer, no connection, or a temporary Google Play error: asking again can work.
    enum class Problem { NONE, TRY_AGAIN, NOT_AVAILABLE }
    @Volatile var problem: Problem = Problem.NONE
        private set
    @Volatile private var cachedPrices: Map<Int, String>? = null
    @Volatile private var prefetching = false

    // The prices already fetched this session, or null if there are none yet.
    fun cachedPrices(): Map<Int, String>? = cachedPrices?.takeIf { it.isNotEmpty() }

    // Fetches the prices in the background as soon as the app opens (and again after login). If Google Play does not
    // answer it asks again quietly, up to 3 more times, a few seconds apart. Does nothing once prices are kept.
    fun prefetch(ctx: Context) {
        if (cachedPrices() != null || prefetching) return
        prefetching = true
        val app = ctx.applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        fun attempt(n: Int) {
            loadPrices(app) { got ->
                if (got.isEmpty() && problem == Problem.TRY_AGAIN && n < 3) {
                    main.postDelayed({ attempt(n + 1) }, 5000L * (n + 1))
                } else {
                    prefetching = false
                }
            }
        }
        attempt(0)
    }
    private val details = HashMap<String, ProductDetails>()
    private val inFlight = HashSet<String>()
    // After this many "no" answers from the database the app stops asking about the same purchase.
    private const val MAX_REFUSALS = 3

    private var onLimitRaised: (() -> Unit)? = null
    private var onMessage: ((String) -> Unit)? = null

    // What the app remembers about each open purchase (by its token): "ok_<token>" = the database confirmed it and
    // only the consume step is left; "no_<token>" = how many times the database refused it.
    private fun state(ctx: Context) = ctx.applicationContext.getSharedPreferences("viewgrow_purchase_state", Context.MODE_PRIVATE)

    // A code for the member a purchase is made for. Google keeps it with the purchase, so a payment is never sent to
    // the database under a different member's login. It is a one-way code, not the phone number.
    private fun accountTag(phone: String): String {
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        val h = java.security.MessageDigest.getInstance("SHA-256").digest(("viewgrow:" + digits).toByteArray(Charsets.UTF_8))
        return h.take(16).joinToString("") { "%02x".format(it) }
    }

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
        // A client that never finished connecting is dropped and made again: asking it to connect a second time
        // can leave the price pop-up waiting for an answer that never comes.
        try { existing?.endConnection() } catch (e: Exception) { }
        val c = BillingClient.newBuilder(app)
            .setListener(updates)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client = c
        var answered = false
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (answered) return
                answered = true
                if (r.responseCode == BillingClient.BillingResponseCode.OK) whenReady(c) else failed()
            }
            override fun onBillingServiceDisconnected() {
                // Google Play dropped the connection (this happens often). Forget it, so the next ask makes a new one.
                if (client === c) client = null
                // Lost before it answered: say so now, so nobody waits. 
                if (answered) return
                answered = true
                failed()
            }
        })
    }

    // Asks Google Play for the three packs. done gets pack size -> price text (empty map if Play is not available).
    // If the first try gets nothing (Google Play slow to connect), it tries once more after 2 seconds.
    fun loadPrices(ctx: Context, done: (Map<Int, String>) -> Unit) {
        queryPrices(ctx) { first ->
            if (first.isNotEmpty()) { done(first); return@queryPrices }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ queryPrices(ctx, done) }, 2000L)
        }
    }

    private fun queryPrices(ctx: Context, done: (Map<Int, String>) -> Unit) {
        connect(ctx, { c ->
            val products = PRODUCT_IDS.values.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
            c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { r, result ->
                val out = HashMap<Int, String>()
                if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (d in result.productDetailsList) {
                        details[d.productId] = d
                        val price = d.oneTimePurchaseOfferDetails?.formattedPrice ?: continue
                        val size = PRODUCT_IDS.entries.firstOrNull { it.value == d.productId }?.key ?: continue
                        out[size] = price
                    }
                    // Google Play answered but listed none of our products: not something asking again fixes.
                    problem = if (out.isEmpty()) Problem.NOT_AVAILABLE else Problem.NONE
                } else {
                    problem = if (r.responseCode == BillingClient.BillingResponseCode.ITEM_UNAVAILABLE) Problem.NOT_AVAILABLE else Problem.TRY_AGAIN
                }
                if (out.isNotEmpty()) cachedPrices = out
                done(out)
            }
        }, { problem = Problem.TRY_AGAIN; done(emptyMap()) })
    }

    // Opens Google Play's own payment sheet for the pack.
    fun buy(activity: Activity, packSize: Int) {
        val id = PRODUCT_IDS[packSize] ?: return
        val d = details[id]
        val c = client
        if (d == null || c == null || !c.isReady) {
            // The pack details are not loaded (yet). Get them from Google Play, then open it.
            loadPrices(activity) { got ->
                if (got.isNotEmpty() && details[id] != null && client?.isReady == true) open(activity, id)
                else onMessage?.invoke("Google Play is not available right now. Try again in a moment.")
            }
            return
        }
        open(activity, id)
    }

    private fun open(activity: Activity, id: String) {
        val d = details[id] ?: return
        val c = client ?: return
        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())
            )
        if (Store.loggedIn) builder.setObfuscatedAccountId(accountTag(Store.phone))
        val params = builder.build()
        val r = c.launchBillingFlow(activity, params)
        if (r.responseCode != BillingClient.BillingResponseCode.OK && r.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            onMessage?.invoke("Could not open Google Play. Try again in a moment.")
        }
    }

    // Looks for purchases that were paid but not yet confirmed with the database (the app closed, no
    // network, the check failed), so a paid pack is never lost. It runs once each time the app starts and when a
    // member logs in, again when the Increase my contact limit pop-up opens (force), and again on the next app
    // open after a check that got no answer. A payment made while the app is open is handled at once (updates).
    fun retryOpenPurchases(ctx: Context, force: Boolean = false) {
        if (!Store.loggedIn) return
        val who = Store.phone
        if (!force && who == lastRetryFor) return
        lastRetryFor = who
        connect(ctx, { c ->
            c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
                if (r.responseCode == BillingClient.BillingResponseCode.OK) list.forEach { handle(it) }
            }
        }, {})
    }

    @Volatile private var lastRetryFor = ""

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
        val ctx = appContext
        // Paid for under another member's login: leave it open until that member logs in. (Purchases made before
        // this code was added carry no tag and are checked as before.)
        val owner = p.accountIdentifiers?.obfuscatedAccountId
        if (!owner.isNullOrEmpty() && owner != accountTag(Store.phone)) { inFlight.remove(token); return }
        // The database already said ok for this one; only the consume step is left. Quiet, no second "Payment received".
        if (ctx != null && state(ctx).getBoolean("ok_$token", false)) { inFlight.remove(token); consume(token); return }
        // The database refused this one several times already: stop asking.
        if (ctx != null && state(ctx).getInt("no_$token", 0) >= MAX_REFUSALS) { inFlight.remove(token); return }
        val phone = Store.phone
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
                ctx?.let { state(it).edit().putBoolean("ok_$token", true).remove("no_$token").apply() }
                // Written down for Profile, Purchase history (only after the database said ok).
                val productId = p.products.firstOrNull() ?: ""
                val pack = PRODUCT_IDS.entries.firstOrNull { it.value == productId }?.key ?: 0
                val price = details[productId]?.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
                appContext?.let { PurchaseLog.add(it, phone, pack, p.purchaseTime, price, p.orderId ?: "") }
                consume(token)
                Status.last = null
                onMessage?.invoke("Payment received. Your contact limit has gone up.")
                onLimitRaised?.invoke()
            } else if (j == null || j.optString("code") == "SESSION_EXPIRED") {
                // No answer, or logged out: the purchase stays open and is checked again after the next log in.
                if (j == null) lastRetryFor = "" // no answer: the next app open checks again
                onMessage?.invoke(if (j == null) "We could not confirm your payment yet. It will be checked again when you open the app."
                                  else "Please log in again. Your payment will be checked as soon as you do.")
            } else {
                // The database said no. Ask again the next few times the app opens, then stop. Say it on the first
                // and the last try, so the member is told without being nagged.
                val n = (ctx?.let { state(it).getInt("no_$token", 0) } ?: 0) + 1
                ctx?.let { state(it).edit().putInt("no_$token", n).apply() }
                if (n == 1) onMessage?.invoke("We could not confirm your payment. Message the admin with your Google Play order number.")
                else if (n >= MAX_REFUSALS) onMessage?.invoke("We have stopped checking your payment. Message the admin with your Google Play order number so they can fix it.")
            }
        }
    }

    private fun consume(token: String) {
        client?.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build()) { r, _ ->
            // Done: forget the notes kept for this purchase.
            val c = appContext
            if (c != null && r.responseCode == BillingClient.BillingResponseCode.OK) state(c).edit().remove("ok_$token").remove("no_$token").apply()
        }
    }
}
