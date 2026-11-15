package com.viewgrow.app

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
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
    private val tabIds = intArrayOf(R.id.tab_home, R.id.tab_history, R.id.tab_profile)
    private val iconIds = intArrayOf(R.id.icon_home, R.id.icon_history, R.id.icon_profile)
    private val labelIds = intArrayOf(R.id.label_home, R.id.label_history, R.id.label_profile)

    // 0 Home, 1 History, 2 Profile. -1 while logged out.
    private var current = -1

    // Height of the phone's status bar. Green headers grow by this much and run behind it.
    private var topInset = 0

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

        // Edge to edge: the green header runs behind the status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        root = findViewById(R.id.root)
        nav = findViewById(R.id.nav)
        Fonts.apply(nav)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            topInset = bars.top
            // Keep the bottom bar above the phone's navigation strip and the keyboard.
            v.setPadding(0, 0, 0, maxOf(bars.bottom, keyboard.bottom))
            supportFragmentManager.fragments.forEach { f -> f.view?.let { padHero(it) } }
            insets
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                padHero(v)
                when (f) {
                    is WelcomeFragment -> chrome(false, R.color.green_dark)
                    is SignUpFragment, is LogInFragment, is TextPageFragment, is PermissionsFragment -> chrome(false, R.color.surface)
                    is HomeFragment, is HistoryFragment, is HistoryDayFragment, is ProfileFragment -> chrome(true, R.color.green_dark)
                }
            }
        }, true)

        tabIds.forEachIndexed { i, id -> findViewById<LinearLayout>(id).setOnClickListener { showTab(i) } }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })

        if (savedInstanceState == null) {
            if (Store.loggedIn) { SyncScheduler.schedule(this); showTab(0) } else showWelcome()
        } else {
            current = savedInstanceState.getInt("current", -1)
            highlight()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current", current)
    }

    // Back: close an opened screen first. Then any tab goes to Home. Back on Home leaves the app
    // without closing it. Back on Welcome closes it.
    private fun onBack() {
        when {
            supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
            current > 0 -> showTab(0)
            current == 0 -> moveTaskToBack(true)
            else -> finish()
        }
    }

    // ---- Screens ----

    fun showWelcome() {
        current = -1
        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction().replace(R.id.container, WelcomeFragment()).commit()
    }

    fun showSignUp() = openAbove(SignUpFragment())

    fun showLogIn() = openAbove(LogInFragment())

    // Sign up and Log in sit one step above Welcome, so Back always returns to Welcome.
    private fun openAbove(f: Fragment) {
        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction().replace(R.id.container, f).addToBackStack(null).commit()
    }

    fun showTab(index: Int) {
        current = index
        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val screen: Fragment = when (index) {
            0 -> HomeFragment()
            1 -> HistoryFragment()
            else -> ProfileFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, screen).commit()
        highlight()
    }

    // Terms and Conditions, Privacy Policy. Back returns to the screen it was opened from.
    fun showPage(kind: Int) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, TextPageFragment.newInstance(kind))
            .addToBackStack(null)
            .commit()
    }

    // A day opened from History. Back returns to the History list.
    fun showHistoryDay(day: String, count: Int) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, HistoryDayFragment.newInstance(day, count))
            .addToBackStack(null)
            .commit()
    }

    // After Sign up or Log in: ask for what is still missing (contacts, notifications, battery),
    // then Home. When everything is already allowed it goes straight to Home.
    fun onLoggedIn() {
        SyncScheduler.schedule(this)
        if (PermissionsFragment.anythingToAsk(this)) showPermissions() else showTab(0)
    }

    private fun showPermissions() {
        current = -1
        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction().replace(R.id.container, PermissionsFragment()).commit()
    }

    fun onLoggedOut() {
        Store.logOut()
        SyncScheduler.cancel(this)
        Status.last = null
        HistoryLog.clear(this)
        Release.clear()
        showWelcome()
    }

    // ---- Look of the window around the screen ----

    // Shows or hides the bottom bar, and paints the strip under it (the phone's navigation area).
    private fun chrome(navVisible: Boolean, colorRes: Int) {
        nav.visibility = if (navVisible) View.VISIBLE else View.GONE
        val color = ContextCompat.getColor(this, colorRes)
        root.setBackgroundColor(color)
        window.navigationBarColor = color
        val lightStrip = colorRes == R.color.surface && !AppTheme.isDark(this)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = lightStrip
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
