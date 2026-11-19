package com.viewgrow.admin

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

// ViewGrow Admin. Three screens in one activity:
//   1. Sign in with the admin key (kept encrypted on this phone, so the sync can run in the background).
//   2. Setup, once: Contacts (to save members) and Notifications.
//   3. The admin website itself (https://viewgrow.site/admin) inside the app. It is the real website, so the app and
//      the website always match. The page gets a small bridge (VGAdminApp) for the app's extras: the key, the
//      "Contacts sync" line, "Log from screenshots", saving files, and going back to Sign in on sign out.
class MainActivity : AppCompatActivity() {

    companion object {
        const val ADMIN_URL = "https://viewgrow.site/admin"
        private val SITE_HOSTS = setOf("viewgrow.site", "www.viewgrow.site")
    }

    private val io = Executors.newSingleThreadExecutor()
    private var web: WebView? = null
    // At launch the splash (icon on green) stays up until the admin page has signed in, then Today shows.
    // There is no in-between "Opening" screen. `cover` is only used to say "No internet connection".
    @Volatile private var pageReady = false
    private var cover: FrameLayout? = null
    private val coverTimeout = Runnable { hideCover() }
    // The host of the page the WebView shows. The bridge only answers pages from viewgrow.site.
    @Volatile private var pageHost = ""
    // Status bar height in dp, for the page's header.
    @Volatile private var topDp = 24
    private var setupStep = 1

    private val contactsAsk = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        setupStep = 2
        if (Build.VERSION.SDK_INT >= 33) showSetup() else finishSetup()
    }
    private val notificationsAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) { finishSetup() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Green splash with the ViewGrow icon (same as the member app's, on green), kept for a moment so it doesn't flash.
        val splashStart = System.currentTimeMillis()
        installSplashScreen().setKeepOnScreenCondition {
            val t = System.currentTimeMillis() - splashStart
            // Short minimum; when the admin is signed in, wait for the page too (at most 10 seconds).
            t < 700 || (web != null && !pageReady && t < 10000)
        }
        super.onCreate(savedInstanceState)
        Store.init(this)
        Ui.edgeToEdge(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else moveTaskToBack(true)
            }
        })
        route()
    }

    override fun onResume() {
        super.onResume()
        // Back from a screenshot log or the Sync screen: refresh the numbers and the sync line on the page.
        web?.evaluateJavascript("window.vgAppRefresh && window.vgAppRefresh()", null)
        if (Store.signedIn && Store.setupDone && Store.syncOn) {
            io.execute {
                MemberSync.run(applicationContext)
                runOnUiThread { web?.evaluateJavascript("window.vgAppRefresh && window.vgAppRefresh()", null) }
            }
        }
    }

    private fun route(message: String = "") {
        when {
            !Store.signedIn -> showSignIn(message)
            !Store.setupDone -> showSetup()
            else -> showWeb()
        }
    }

    // ---------- 1. Sign in ----------

    private fun showSignIn(message: String) {
        webRoot?.removeCallbacks(coverTimeout); cover = null
        web?.destroy(); web = null
        plainNavBar()
        val (root, body) = Ui.page(this, Ui.hero(this, "ViewGrow Admin", "For the admin only", logo = true))
        body.addView(Ui.text(this, "Admin key", 12.5f, R.color.text2, "semibold"))
        val key = Ui.field(this, "Your admin key", true)
        body.addView(key)
        val err = Ui.text(this, message, 13f, R.color.red_dark, top = 10)
        err.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE
        body.addView(err)
        lateinit var go: TextView
        go = Ui.button(this, "Sign in", top = 18) {
            val k = key.text.toString().trim()
            if (k.isEmpty()) { err.text = "Type your admin key."; err.visibility = View.VISIBLE; return@button }
            Ui.setBusy(go, true, "Sign in", "Signing in")
            io.execute {
                val r = Api.request("web_admin_stats", JSONObject().put("p_key", k))
                runOnUiThread {
                    Ui.setBusy(go, false, "Sign in")
                    val j = r.json
                    when {
                        j == null -> { err.text = Api.netMessage(r.error); err.visibility = View.VISIBLE }
                        !j.optBoolean("ok") -> {
                            err.text = if (j.optString("code") == "LOCKED") "Too many wrong keys. Try again in 15 minutes." else "Wrong admin key."
                            err.visibility = View.VISIBLE
                        }
                        else -> { Store.key = k; route() }
                    }
                }
            }
        }
        body.addView(go)
        body.addView(Ui.text(this, "The same key you use on viewgrow.site/admin. It stays on this phone, so the app can save new members in the background.", 12.5f, R.color.muted, center = true, top = 14))
        setContentView(root)
    }

    // ---------- 2. Setup ----------

    private fun showSetup() {
        plainNavBar()
        val contacts = setupStep == 1
        val header = stepHero(if (contacts) "Save members" else "Stay notified", setupStep)
        val (root, body) = Ui.page(this, header)
        val card = Ui.card(this, 0, 24)
        card.gravity = Gravity.CENTER_HORIZONTAL
        // The same full-colour pictures as the member app's permission steps (no emoji).
        val icon = ImageView(this)
        icon.setImageResource(if (contacts) R.drawable.ob_contacts else R.drawable.ob_bell)
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        icon.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 88))
        card.addView(icon)
        card.addView(Ui.text(this,
            if (contacts) "The app saves every new member to your phone as “Name VG”, so you can see their status and they can see yours. It also looks up the names on your screenshots. Numbers already in your phone are left as they are."
            else "A quiet note when new members are saved to your phone. You can turn it off any time.",
            14.5f, R.color.text, center = true, top = 20))
        body.addView(card)
        body.addView(Ui.button(this, if (contacts) "Allow Contacts" else "Allow Notifications", top = 24) {
            if (contacts) contactsAsk.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
            else if (Build.VERSION.SDK_INT >= 33) notificationsAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
            else finishSetup()
        })
        setContentView(root)
    }

    // The setup header, same as the member app's permission steps: "STEP 1 OF 2", the title, and two dashes
    // (the first N filled on step N).
    private fun stepHero(title: String, step: Int): LinearLayout {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.VERTICAL
        h.gravity = Gravity.CENTER_HORIZONTAL
        val g = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(Ui.color(this, R.color.hero_start), Ui.color(this, R.color.green), Ui.color(this, R.color.hero_end)))
        val r = Ui.dp(this, 32).toFloat()
        g.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        h.background = g
        h.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 26))
        h.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val counter = Ui.text(this, "STEP $step OF 2", 12f, R.color.white, "semibold", center = true)
        counter.letterSpacing = 0.08f
        counter.alpha = 0.85f
        h.addView(counter)
        h.addView(Ui.text(this, title, 26f, R.color.white, "extrabold", center = true, top = 8))
        val dashes = LinearLayout(this)
        dashes.orientation = LinearLayout.HORIZONTAL
        dashes.layoutParams = Ui.lp(this, 18)
        for (i in 1..2) {
            val d = View(this)
            d.background = Ui.shape(this, if (i <= step) 0xFFFFFFFF.toInt() else 0x59FFFFFF, 3)
            d.layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this, 5), 1f).also {
                it.marginStart = if (i == 1) 0 else Ui.dp(this, 4)
                it.marginEnd = if (i == 2) 0 else Ui.dp(this, 4)
            }
            dashes.addView(d)
        }
        h.addView(dashes)
        return h
    }

    private fun finishSetup() {
        Store.setupDone = true
        MemberSync.schedule(this)
        // The first check saves every current member.
        io.execute { MemberSync.run(applicationContext, full = true); runOnUiThread { web?.evaluateJavascript("window.vgAppRefresh && window.vgAppRefresh()", null) } }
        showWeb()
    }

    // ---------- 3. The admin website ----------

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun showWeb() {
        webRoot?.removeCallbacks(coverTimeout); cover = null
        MemberSync.schedule(this)
        val root = FrameLayout(this)
        // The phone's bottom area (under the home line) is painted by the app; the page says which colour (see bar()).
        strip = BottomStrip(root, Ui.color(this, R.color.surface))
        root.background = strip
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        pageBar = intArrayOf(0, 0)
        paintPageStrip()
        val w = WebView(this)
        w.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        w.setBackgroundColor(Ui.color(this, R.color.surface))
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.setSupportZoom(false)
        // The admin page is laid out for normal text size; a very large phone font would push buttons off the screen.
        w.settings.textZoom = 100
        w.addJavascriptInterface(Bridge(), "VGAdminApp")
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                // Only a secure (https) page can be "ours": a plain http page, or one with no address, gets no answers.
                val u = Uri.parse(url ?: "")
                pageHost = if (u.scheme == "https") (u.host ?: "") else ""
            }
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript("window.vgAppTop && window.vgAppTop($topDp)", null)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                // The admin page itself could not load (no internet): show Try again on the cover.
                if (request.isForMainFrame) coverOffline()
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if ((u.scheme == "https") && SITE_HOSTS.contains(u.host ?: "")) return false
                // WhatsApp, Telegram and other links open in their own apps.
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: ActivityNotFoundException) { Ui.toast(this@MainActivity, "Could not open the link.") }
                return true
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            // The page's own questions (confirm / alert) use the app's pop-up, not the plain system one.
            private fun split(message: String?): Pair<String, String> {
                val m = (message ?: "").trim()
                val i = m.indexOf("\n\n")
                return if (i > 0) m.substring(0, i) to m.substring(i + 2).trim() else m to ""
            }
            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                val (t, m) = split(message)
                Ui.confirm(this@MainActivity, t, m, onCancel = { result.cancel() }, onOk = { result.confirm() })
                return true
            }
            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                val (t, m) = split(message)
                Ui.notice(this@MainActivity, t, m, onClose = { result.confirm() })
                return true
            }
        }
        root.addView(w)
        // The page's green header runs under the status bar; the page is told how tall the status bar is.
        Ui.fitInsets(root, onTop = { px ->
            topDp = Math.round(px / resources.displayMetrics.density)
            web?.evaluateJavascript("window.vgAppTop && window.vgAppTop($topDp)", null)
        })
        setContentView(root)
        web = w
        webRoot = root
        pageReady = false
        w.loadUrl(ADMIN_URL)
        // If the page never says it's ready (an older website), let the splash go anyway after a while.
        root.removeCallbacks(coverTimeout)
        root.postDelayed(coverTimeout, 10000)
    }
    private var webRoot: FrameLayout? = null

    // ---- The strip under the page (the phone's navigation area) ----
    private var strip: BottomStrip? = null
    private var pageBar = intArrayOf(0, 0)   // the colours the page asked for (0 = not told yet)

    // Paints the page colour, and the strip along the bottom edge (as tall as the root's bottom padding, which is the
    // navigation bar). Left to right, like the bottom bar, so the bar and the strip look like one piece.
    class BottomStrip(private val host: View, private val page: Int) : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint()
        private var a = page
        private var b = page
        fun set(start: Int, end: Int) { a = start; b = end; invalidateSelf(); host.invalidate() }
        val isLight: Boolean get() = androidx.core.graphics.ColorUtils.calculateLuminance(a) > 0.5
        override fun draw(c: android.graphics.Canvas) {
            val r = bounds
            paint.shader = null
            paint.color = page
            c.drawRect(r, paint)
            val h = host.paddingBottom
            if (h <= 0) return
            paint.shader = android.graphics.LinearGradient(r.left.toFloat(), 0f, r.right.toFloat(), 0f, a, b, android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(r.left.toFloat(), (r.bottom - h).toFloat(), r.right.toFloat(), r.bottom.toFloat(), paint)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
    }

    // The app's own screens (Sign in, Setup) keep the phone's normal navigation bar, as before.
    private fun plainNavBar() {
        strip = null
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.BLACK
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
    }

    private fun paintStrip(start: Int, end: Int) {
        val s = strip ?: return
        s.set(start, end)
        // White icons (home line, buttons) on green, dark ones on white.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = s.isLight
    }

    // Back to what the page asked for (after the green "No internet" cover goes).
    private fun paintPageStrip() {
        if (pageBar[0] != 0 || pageBar[1] != 0) paintStrip(pageBar[0], pageBar[1])
        else paintStrip(Ui.color(this, R.color.surface), Ui.color(this, R.color.surface))
    }

    // Only for "No internet connection": the app's green gradient, the logo, the message and Try again.
    private fun buildCover(): FrameLayout {
        val c = FrameLayout(this)
        c.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        c.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(Ui.color(this, R.color.hero_start), Ui.color(this, R.color.green), Ui.color(this, R.color.hero_end)))
        c.isClickable = true
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        val img = ImageView(this)
        img.setImageResource(R.drawable.viewgrow_logo)
        img.background = Ui.shape(this, Ui.color(this, R.color.white), 22)
        img.clipToOutline = true
        img.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76))
        col.addView(img)
        val msg = Ui.text(this, "No internet connection", 15f, R.color.white, "semibold", center = true, top = 18)
        msg.tag = "msg"
        col.addView(msg)
        val bar = android.widget.ProgressBar(this)
        bar.indeterminateTintList = android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.white))
        val lp = LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34))
        lp.topMargin = Ui.dp(this, 16)
        bar.layoutParams = lp
        bar.tag = "bar"
        bar.visibility = View.GONE
        col.addView(bar)
        c.addView(col)
        return c
    }

    // The page is signed in (or gave up waiting): the splash can go, and any "No internet" cover too.
    private fun hideCover() {
        pageReady = true
        webRoot?.removeCallbacks(coverTimeout)
        val c = cover ?: return
        cover = null
        paintPageStrip()
        c.animate().alpha(0f).setDuration(220).withEndAction { (c.parent as? ViewGroup)?.removeView(c) }.start()
    }

    // No internet while opening: keep the cover, say so, and offer Try again (the key is kept).
    private fun coverOffline() {
        pageReady = true
        webRoot?.removeCallbacks(coverTimeout)
        val c = cover ?: buildCover().also { nc -> webRoot?.addView(nc); cover = nc }
        paintStrip(Ui.color(this, R.color.green), Ui.color(this, R.color.hero_end))
        val col = c.getChildAt(0) as LinearLayout
        (col.findViewWithTag<TextView>("msg"))?.text = "No internet connection"
        col.findViewWithTag<View>("bar")?.visibility = View.GONE
        if (col.findViewWithTag<View>("retry") == null) {
            val b = Ui.text(this, "Try again", 15f, R.color.green_dark, "semibold", center = true, top = 18)
            b.tag = "retry"
            b.setPadding(Ui.dp(this, 28), Ui.dp(this, 12), Ui.dp(this, 28), Ui.dp(this, 12))
            b.background = Ui.shape(this, Ui.color(this, R.color.white), 999)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Ui.dp(this, 18)
            b.layoutParams = lp
            b.setOnClickListener {
                // Take the message away and load the page again; it comes back if there's still no internet.
                cover = null
                (c.parent as? ViewGroup)?.removeView(c)
                paintPageStrip()
                web?.reload()
            }
            col.addView(b)
        }
    }

    // What the admin page can ask the app. Runs on a background thread; only viewgrow.site pages get answers.
    inner class Bridge {
        private fun ours() = SITE_HOSTS.contains(pageHost)

        @JavascriptInterface
        fun key(): String = if (ours()) Store.key else ""

        // The page signed out (wrong key, locked, or the Sign out button): back to the app's Sign in.
        @JavascriptInterface
        fun signedOut(msg: String) {
            if (!ours()) return
            runOnUiThread {
                Store.key = ""
                MemberSync.cancel(this@MainActivity)
                showSignIn(if (msg.isBlank()) "" else msg)
            }
        }

        // The page has signed in and is showing Today: the splash goes.
        @JavascriptInterface
        fun ready() { if (ours()) runOnUiThread { hideCover() } }

        // The page could not reach the server while signing in.
        @JavascriptInterface
        fun offline() { if (ours()) runOnUiThread { coverOffline() } }

        // The colour for the phone's bottom area, from the page: the green bar's two colours, or one colour twice
        // (an open pop-up, a full screen, or the plain page). "#rrggbb".
        @JavascriptInterface
        fun bar(start: String, end: String) {
            if (!ours()) return
            val a = try { android.graphics.Color.parseColor(start) } catch (e: Exception) { return }
            val b = try { android.graphics.Color.parseColor(end) } catch (e: Exception) { a }
            runOnUiThread {
                pageBar = intArrayOf(a, b)
                if (cover == null) paintStrip(a, b)
            }
        }

        // How tall the status bar is (dp), so the page's green header can sit under it.
        @JavascriptInterface
        fun topInset(): Int = if (ours()) topDp else 0

        @JavascriptInterface
        fun syncText(): String = if (ours()) MemberSync.lineHtml(this@MainActivity) else ""

        @JavascriptInterface
        fun openSync() { if (ours()) runOnUiThread { startActivity(Intent(this@MainActivity, SyncActivity::class.java)) } }

        @JavascriptInterface
        fun openScreenshots() { if (ours()) runOnUiThread { startActivity(Intent(this@MainActivity, ShotsActivity::class.java)) } }

        // A file from the page (New members, Backup, PDF, CSV): saved to Downloads.
        @JavascriptInterface
        fun saveFile(name: String, mime: String, base64: String) {
            if (!ours()) return
            val bytes = try { Base64.decode(base64, Base64.DEFAULT) } catch (e: Exception) { null }
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "viewgrow-file" }
            val uri = if (bytes == null) null else saveToDownloads(safe, mime, bytes)
            runOnUiThread {
                if (uri == null) { Ui.toast(this@MainActivity, "Could not save the file."); return@runOnUiThread }
                Ui.toast(this@MainActivity, "Saved to Downloads: $safe")
                // A contact file opens straight away, so the contacts can be added.
                if (safe.endsWith(".vcf") && uri.scheme == "content") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/x-vcard").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (e: ActivityNotFoundException) { }
                }
            }
        }
    }

    // Android 10 and newer: the shared Downloads folder. Older: the app's own Downloads folder.
    private fun saveToDownloads(name: String, mime: String, bytes: ByteArray): Uri? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime.ifEmpty { "application/octet-stream" })
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            if (uri != null) contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            uri
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            val f = File(dir, name)
            f.writeBytes(bytes)
            Uri.fromFile(f)
        }
    } catch (e: Exception) { null }

    override fun onDestroy() {
        web?.destroy()
        super.onDestroy()
    }
}
