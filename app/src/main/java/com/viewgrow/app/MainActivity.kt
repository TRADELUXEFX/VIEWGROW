package com.viewgrow.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

// One screen host. Welcome, Sign up and Log in have no bottom bar. Home, History and Profile share it.
class MainActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_MIN_MS = 700L
    }

    private lateinit var root: View
    private lateinit var nav: LinearLayout
    private lateinit var fab: View
    private val tabIds = intArrayOf(R.id.tab_home, R.id.tab_history, R.id.tab_profile)
    private val iconIds = intArrayOf(R.id.icon_home, R.id.icon_history, R.id.icon_profile)
    private val labelIds = intArrayOf(R.id.label_home, R.id.label_history, R.id.label_profile)

    // 0 Home, 1 History, 2 Profile. -1 while logged out.
    private var current = -1

    // Height of the phone's status bar. Green headers grow by this much and run behind it.
    private var topInset = 0

    // Height of the phone's navigation strip. The green gradient runs behind it too.
    private var bottomInset = 0

    // False only on the Banned and Blocked screens, where the strip stays the page colour.
    private var greenStrip = true

    // Every screen goes through ScaleGuard, so the phone's Font size and Display size settings
    // cannot stretch the app past what the layouts were built for.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ScaleGuard.wrap(newBase))
    }

    // AppCompat applies the phone's own configuration on top of ours. Keep our scale, but let it
    // keep its own light/dark mode (uiMode).
    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Store.init(applicationContext)
        AppTheme.apply(this)
        val splashStart = System.currentTimeMillis()
        installSplashScreen().setKeepOnScreenCondition {
            System.currentTimeMillis() - splashStart < SPLASH_MIN_MS
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Load the admin's settings right away, so by the time Log in, Sign up or Home opens they are usually ready.
        Remote.load { }

        // Edge to edge: the green header runs behind the status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        root = findViewById(R.id.root)
        nav = findViewById(R.id.nav)
        // Floating chat button (same bubble as VGContact): opens the admin on WhatsApp.
        fab = findViewById(R.id.fab_chat)
        fab.setOnClickListener {
            if (!Remote.openAdmin(this, "Hi, I need help with ViewGrow. My name is ${Store.name}.")) {
                Ui.toast(root, "Could not open the link.")
            }
        }
        Fonts.apply(nav)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            topInset = bars.top
            bottomInset = bars.bottom
            paintStrip()
            // Keep the bottom bar above the phone's navigation strip and the keyboard.
            v.setPadding(0, 0, 0, maxOf(bars.bottom, keyboard.bottom))
            supportFragmentManager.fragments.forEach { f -> f.view?.let { padHero(it) } }
            insets
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                padHero(v)
                when (f) {
                    is WelcomeFragment -> chrome(false, R.color.surface)
                    is SignUpFragment, is LogInFragment, is TextPageFragment, is FaqFragment, is DeleteAccountFragment, is PurchaseHistoryFragment, is PermissionsFragment -> chrome(false, R.color.surface)
                    is StopFragment -> chrome(false, R.color.surface, greenStripOn = false)
                    is HomeFragment, is HistoryFragment, is HistoryDayFragment, is ProfileFragment -> chrome(true, R.color.green_dark)
                }
                // The chat button shows on Home, History and Profile only.
                fab.visibility = if (f is HomeFragment || f is HistoryFragment || f is ProfileFragment) View.VISIBLE else View.GONE
            }
        }, true)

        tabIds.forEachIndexed { i, id -> findViewById<LinearLayout>(id).setOnClickListener { showTab(i) } }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })

        if (savedInstanceState == null) {
            if (Store.banned) showBanned()
            else if (Store.loggedIn) { SyncScheduler.schedule(this); showTab(0) }
            else showWelcome()
        } else {
            current = savedInstanceState.getInt("current", -1)
            highlight()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current", current)
    }

    // A ban can arrive while the app is in the background (the automatic sync hears it first).
    // Coming back, the Banned screen takes over.
    override fun onResume() {
        super.onResume()
        val stop = supportFragmentManager.findFragmentById(R.id.container) as? StopFragment
        if (Store.banned && (stop == null || !stop.isBanned)) showBanned()
        // Back from the phone's Settings: the permission may have changed. Home shows its own banner;
        // this just clears the old notification and the "already warned" flag once it is back on.
        PermissionWatch.onAppOpen(this)
        ReshareReminder.show(this)
        // Packs paid for but not yet confirmed with the database are tried again here.
        BillingManager.listen(
            { msg -> android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show() },
            { refreshAfterPurchase() }
        )
        BillingManager.retryOpenPurchases(this)
        // Opened from the "permission is off" notification: go straight to the fix, no pop-up first.
        if (intent?.getBooleanExtra(PermissionWatch.EXTRA_FIX, false) == true) {
            intent.removeExtra(PermissionWatch.EXTRA_FIX)
            if (Store.loggedIn && !Store.banned && PermissionWatch.missing(this).isNotEmpty()) {
                permPopupAt = System.currentTimeMillis()
                fixPermission()
                return
            }
        }
        // Opened from the "80% of your limit used" notification: show Increase my contact limit.
        if (intent?.getBooleanExtra(LimitAlert.EXTRA_UPGRADE, false) == true) {
            intent.removeExtra(LimitAlert.EXTRA_UPGRADE)
            if (current >= 0 && Store.loggedIn && !Store.banned && !notVerified()) {
                UpgradeSheet.show(this, Store.lastTotal, Store.lastLimit)
                return
            }
        }
        maybeShowPermissionPopup()
    }

    // The limit went up: Home asks the database again so the new limit shows at once.
    fun refreshAfterPurchase() {
        Status.last = null
        if (current == 0) {
            current = -1
            showTab(0)
        }
    }

    // The notification can open the app that is already running.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    // ---- Fix a permission that is off (used by the Home box and the pop-up) ----

    private val contactsAsk = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (!ContactsSaver.hasPermission(this) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CONTACTS)
        ) openAppSettings()
    }

    private val notificationsAsk = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && android.os.Build.VERSION.SDK_INT >= 33 &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) openAppSettings()
    }

    // Takes the first thing that is off and asks for it, or opens the phone's own settings for it.
    // When the member comes back, Home looks again and the box goes away if it is fixed.
    fun fixPermission() {
        val off = PermissionWatch.missing(this)
        when (off.firstOrNull()) {
            "Contacts" -> ContactsDisclosure.show(this) {
                contactsAsk.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
            }
            "Notifications" ->
                if (android.os.Build.VERSION.SDK_INT >= 33) notificationsAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                else openNotificationSettings()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (e: ActivityNotFoundException) {
            // No settings screen to open.
        }
    }

    private fun openNotificationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } catch (e: ActivityNotFoundException) {
            openAppSettings()
        }
    }

    // ---- "A permission is off" pop-up ----

    private var permDialog: android.app.Dialog? = null
    private var permPopupAt = 0L

    // Inside the app: when it opens or returns from Settings and Contacts or Notifications is
    // off, a pop-up names what is off. Only on Home, History and Profile (not during sign up or the
    // permission steps), never twice in a row
    // within 10 seconds (the phone's own permission box also brings the app back to the front).
    private fun maybeShowPermissionPopup() {
        if (permDialog?.isShowing == true) return
        if (current < 0 || !Store.loggedIn || Store.banned || notVerified()) return
        if (System.currentTimeMillis() - permPopupAt < 10000L) return
        val off = PermissionWatch.missing(this)
        if (off.isEmpty()) return
        permPopupAt = System.currentTimeMillis()

        val v = layoutInflater.inflate(R.layout.dialog_permission, null)
        v.findViewById<TextView>(R.id.perm_title).text =
            if (off.size == 1) "${off[0]} is off" else "Some permissions are off"
        val rows = v.findViewById<LinearLayout>(R.id.perm_rows)
        val red = ContextCompat.getColor(this, R.color.red_dark)
        for (name in off) {
            val why = when (name) {
                "Contacts" -> "to save your contacts"
                "Notifications" -> "to tell you when contacts are saved"
                else -> "so ViewGrow can tell you what happened"
            }
            val row = TextView(this)
            row.textSize = 14f
            row.setTextColor(ContextCompat.getColor(this, R.color.text))
            row.setBackgroundResource(R.drawable.bg_notice_red)
            row.setPadding(Ui.dp(this, 14), Ui.dp(this, 11), Ui.dp(this, 14), Ui.dp(this, 11))
            row.text = Ui.lead(this, name, "\u00b7 $why", red)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Ui.dp(this, 8)
            row.layoutParams = lp
            rows.addView(row)
        }
        val dialog = android.app.Dialog(this, R.style.Theme_ViewGrow_Dialog)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        Fonts.apply(v)
        dialog.setContentView(v)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(resources.displayMetrics.widthPixels - Ui.dp(this, 44), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(android.view.Gravity.CENTER)
        v.findViewById<TextView>(R.id.perm_fix).setOnClickListener {
            dialog.dismiss()
            fixPermission()
        }
        v.findViewById<TextView>(R.id.perm_later).setOnClickListener { dialog.dismiss() }
        permDialog = dialog
        dialog.show()
    }

    override fun onDestroy() {
        // The billing helper lives as long as the app, so it must not keep this screen alive.
        BillingManager.stopListening()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        // A pop-up left open while the app is in the background is rebuilt fresh on return.
        permDialog?.dismiss()
    }

    // Back: close an opened screen first. Then any tab goes to Home. Back on Home leaves the app
    // without closing it. Back on Welcome closes it.
    private fun onBack() {
        // Banned: nowhere to go back to, so leave the app. Blocked: back to Welcome.
        val stop = supportFragmentManager.findFragmentById(R.id.container) as? StopFragment
        if (stop != null) {
            if (stop.isBanned) moveTaskToBack(true) else showWelcome()
            return
        }
        when {
            supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
            isSignUpOrLogIn() -> showWelcome()
            current > 0 -> showTab(0)
            current == 0 -> moveTaskToBack(true)
            else -> finish()
        }
    }

    // Puts a screen in place of whatever is showing and empties the back stack. The stack is cleared in the
    // same step as the swap (not before it), so a page that was underneath (Profile under Terms, History
    // under a day) is never rebuilt for a moment first. That brief rebuild was the flash.
    private fun swap(f: Fragment) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction().setReorderingAllowed(true).replace(R.id.container, f).commit()
    }

    private fun isSignUpOrLogIn(): Boolean {
        val f = supportFragmentManager.findFragmentById(R.id.container)
        return f is SignUpFragment || f is LogInFragment
    }

    // ---- Screens ----

    fun showWelcome() {
        current = -1
        swap(WelcomeFragment())
    }

    // Full-screen Banned page (StopFragment). Replaces whatever is showing, with nothing to go back to.
    fun showBanned() {
        current = -1
        swap(StopFragment.banned())
    }

    // Full-screen Blocked page: the password is locked. "Back to log in" opens Log in above Welcome.
    fun showBlocked(phone: String) {
        current = -1
        swap(StopFragment.blocked(phone))
    }

    fun showSignUp() = openAbove(SignUpFragment())

    fun showLogIn() = openAbove(LogInFragment())

    // Sign up and Log in replace each other directly. They are not on the back stack: popping the stack
    // used to bring Welcome back for a moment (a flash of the Welcome page and its green window colour)
    // before the next page replaced it. Back from either one is handled in onBack and goes to Welcome.
    private fun openAbove(f: Fragment) {
        current = -1
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.container, f)
            .commit()
    }

    // History and Profile are for verified members only. The last answer from the database decides:
    // "NOT_VERIFIED" keeps the member on Home with a short message. No answer yet does not block.
    private fun notVerified(): Boolean = Status.last?.optString("state") == "NOT_VERIFIED"

    // The pop-up a member who is not verified gets when tapping History or Profile.
    private fun showVerifyPopup() {
        val v = layoutInflater.inflate(R.layout.dialog_verify, null)
        val link = Remote.adminLink("Hi, I just registered on ViewGrow. My name is ${Store.name}.")
        val dialog = android.app.Dialog(this, R.style.Theme_ViewGrow_Dialog)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        Fonts.apply(v)
        dialog.setContentView(v)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(resources.displayMetrics.widthPixels - Ui.dp(this, 44), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(android.view.Gravity.CENTER)
        val admin = v.findViewById<TextView>(R.id.verify_admin)
        if (link == null) admin.visibility = View.GONE
        admin.setOnClickListener {
            dialog.dismiss()
            try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link)))
            } catch (e: android.content.ActivityNotFoundException) {
                Ui.toast(nav, "Could not open the link.")
            }
        }
        v.findViewById<TextView>(R.id.verify_close).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun showTab(index: Int) {
        if (index > 0 && notVerified()) {
            if (current != 0) showTab(0)
            showVerifyPopup()
            return
        }
        // Tapping the tab already showing does nothing (it used to rebuild the page and flash).
        val showing = supportFragmentManager.findFragmentById(R.id.container)
        if (index == current && showing != null && supportFragmentManager.backStackEntryCount == 0) return
        current = index
        val screen: Fragment = when (index) {
            0 -> HomeFragment()
            1 -> HistoryFragment()
            else -> ProfileFragment()
        }
        swap(screen)
        highlight()
    }

    // Terms and Conditions, Privacy Policy. Back returns to the screen it was opened from.
    // Opens the website page in an in-app browser; the text pages are only the fallback when the phone has no browser.
    fun showPage(kind: Int) {
        if (Web.open(this, if (kind == Pages.PRIVACY) Web.PRIVACY else Web.TERMS)) return
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.container, TextPageFragment.newInstance(kind))
            .addToBackStack(null)
            .commit()
    }

    // Delete my account, opened from Profile. Back returns to Profile.
    fun showDeleteAccount() {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.container, DeleteAccountFragment())
            .addToBackStack(null)
            .commit()
    }

    // Frequently asked questions, opened from Profile. Back returns to Profile.
    fun showFaq() {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.container, FaqFragment())
            .addToBackStack(null)
            .commit()
    }

    // Purchase history, opened from Profile. Back returns to Profile.
    fun showPurchases() {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.container, PurchaseHistoryFragment())
            .addToBackStack(null)
            .commit()
    }

    // A day opened from History. Back returns to the History list.
    fun showHistoryDay(day: String, count: Int) {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.container, HistoryDayFragment.newInstance(day, count))
            .addToBackStack(null)
            .commit()
    }

    // After Sign up or Log in: ask for what is still missing (contacts, notifications),
    // then Home. When everything is already allowed it goes straight to Home.
    fun onLoggedIn() {
        SyncScheduler.schedule(this)
        ReshareReminder.show(this)
        if (PermissionsFragment.anythingToAsk(this)) showPermissions() else showTab(0)
    }

    private fun showPermissions() {
        current = -1
        swap(PermissionsFragment())
    }

    // goWelcome = false when another full screen (Blocked) is about to take over.
    fun onLoggedOut(goWelcome: Boolean = true) {
        Store.logOut()
        SyncScheduler.cancel(this)
        ReshareReminder.cancel(this)
        Status.last = null
        HistoryLog.clear(this)
        Release.clear()
        if (goWelcome) showWelcome()
    }

    // ---- Look of the window around the screen ----

    // Shows or hides the bottom bar, and paints the strip under it (the phone's navigation area).
    // The strip is the same green gradient as the bottom bar and the headers, so the screen has no dark band at the bottom.
    private fun chrome(navVisible: Boolean, colorRes: Int, greenStripOn: Boolean = true) {
        nav.visibility = if (navVisible) View.VISIBLE else View.GONE
        greenStrip = greenStripOn
        // The window's own navigation bar is see-through, so what we paint in the root shows behind it.
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        // White icons on the green strip. On the plain strip (Banned, Blocked) they follow light or dark mode.
        val lightStrip = !greenStripOn && !AppTheme.isDark(this)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = lightStrip
        paintStrip()
    }

    // Root background: the page colour, with the gradient strip along the bottom edge.
    // Left to right, like the bottom bar, so the bar and the strip look like one piece.
    private fun paintStrip() {
        if (!::root.isInitialized) return
        val page = ColorDrawable(ContextCompat.getColor(this, R.color.surface))
        val strip: android.graphics.drawable.Drawable = if (greenStrip) {
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(ContextCompat.getColor(this, R.color.green), ContextCompat.getColor(this, R.color.green_dark))
            )
        } else {
            ColorDrawable(ContextCompat.getColor(this, R.color.surface))
        }
        val layers = LayerDrawable(arrayOf(page, strip))
        layers.setLayerGravity(1, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
        layers.setLayerHeight(1, bottomInset)
        root.background = layers
    }

    // Gives a screen's green header extra top padding equal to the status bar, so it fills the space behind it.
    private fun padHero(screen: View) {
        val hero = screen.findViewById<View>(R.id.hero) ?: (if (screen.id == R.id.hero) screen else null) ?: return
        val base = (hero.getTag(R.id.hero_base) as? Int) ?: hero.paddingTop.also { hero.setTag(R.id.hero_base, it) }
        hero.setPadding(hero.paddingLeft, base + topInset, hero.paddingRight, hero.paddingBottom)
    }

    private fun highlight() {
        tabIds.forEachIndexed { i, _ ->
            val color = if (i == current) Color.WHITE else Color.argb(170, 255, 255, 255)
            ImageViewCompat.setImageTintList(findViewById<ImageView>(iconIds[i]), ColorStateList.valueOf(color))
            findViewById<TextView>(labelIds[i]).setTextColor(color)
        }
    }
}
