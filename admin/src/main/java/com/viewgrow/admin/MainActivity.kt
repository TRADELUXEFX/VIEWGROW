package com.viewgrow.admin

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
import androidx.appcompat.app.AlertDialog
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
    // The host of the page the WebView shows. The bridge only answers pages from viewgrow.site.
    @Volatile private var pageHost = ""
    private var setupStep = 1

    private val contactsAsk = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        setupStep = 2
        if (Build.VERSION.SDK_INT >= 33) showSetup() else finishSetup()
    }
    private val notificationsAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) { finishSetup() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
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
        web?.destroy(); web = null
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
        val contacts = setupStep == 1
        val header = Ui.hero(this, if (contacts) "Save members" else "Stay notified", "Step $setupStep of 2")
        val (root, body) = Ui.page(this, header)
        val card = Ui.card(this, 0, 24)
        card.gravity = Gravity.CENTER_HORIZONTAL
        val icon = Ui.text(this, if (contacts) "👤" else "🔔", 34f, center = true)
        icon.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 88))
        icon.background = Ui.shape(this, Ui.color(this, R.color.tint), 999)
        icon.gravity = Gravity.CENTER
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
        MemberSync.schedule(this)
        val root = FrameLayout(this)
        root.setBackgroundColor(Ui.color(this, R.color.green))
        val w = WebView(this)
        w.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        w.setBackgroundColor(Ui.color(this, R.color.surface))
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.setSupportZoom(false)
        w.addJavascriptInterface(Bridge(), "VGAdminApp")
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                pageHost = Uri.parse(url ?: "").host ?: ""
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
            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setNegativeButton("Cancel") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.confirm() }
                    .show()
                return true
            }
        }
        root.addView(w)
        Ui.fitInsets(root)
        setContentView(root)
        web = w
        w.loadUrl(ADMIN_URL)
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
