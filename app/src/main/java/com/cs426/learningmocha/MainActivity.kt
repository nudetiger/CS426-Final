package com.cs426.learningmocha

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.cs426.learningmocha.databinding.ActivityMainBinding
import com.cs426.learningmocha.ui.common.AppTheme
import com.cs426.learningmocha.ui.onboarding.OnboardingActivity
import com.cs426.learningmocha.ui.onboarding.TutorialCoach

/**
 * Single-activity shell: NavHost + bottom navigation with the five top-level tabs
 * (Home, Browse, Search, AI, Settings). Reader/editor sit on the back stack and hide the tabs.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val settings by lazy { (application as LearningMochaApp).settings }

    /** Held only while the coach marks are on screen, so they can be closed with the window. */
    private var coach: TutorialCoach? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before anything inflates, and before enableEdgeToEdge reads the theme's bar colours.
        // The manifest names Theme.LearningMocha so the launch window has something to draw;
        // this is where a named palette (Rose Pine, Catppuccin, Nord) takes over.
        setTheme(AppTheme.of(settings.themeKey).styleRes)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
                    or WindowInsetsCompat.Type.ime(),
            )
            v.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Snapped rather than slid: a destination change already animates the whole screen,
            // and a second 180 ms slide underneath it reads as the bar lagging behind.
            setBottomNavVisible(destination.id in TABS_VISIBLE_ON, animate = false)
            // Navigating is an answer too: the walkthrough ends rather than following the user
            // to a screen its current card is not about.
            coach?.skip()
        }

        // Onboarding sits on top of this activity rather than replacing it, so finishing it
        // simply reveals Home with the profile already filled in.
        if (!settings.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    /**
     * Shows or hides the tab bar. The reader drives this from its scroll position so the tabs
     * are out of the way while you read and back the moment you reach for them; every other
     * screen only ever gets the [animate]-less call from the destination listener.
     *
     * The bar is a LinearLayout sibling of the NavHost rather than an overlay, so hiding it
     * gives the content its height back. That is deliberate: an overlaying bar would have to be
     * paid for with bottom padding on all five tab screens, and a post is the one place where
     * the extra 56dp of prose is worth having.
     */
    fun setBottomNavVisible(visible: Boolean, animate: Boolean = true) {
        val nav = binding.bottomNav
        if ((nav.visibility == View.VISIBLE) == visible) return
        if (animate) {
            TransitionManager.beginDelayedTransition(
                binding.root,
                Slide(Gravity.BOTTOM).apply {
                    duration = NAV_SLIDE_MS
                    addTarget(nav)
                },
            )
        }
        nav.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        // Runs on the first frame after onboarding finishes, and never again: `onboarded` is
        // still false while the welcome screen is on top, and `tutorialSeen` is set by the
        // walkthrough itself whether it was finished or skipped.
        if (settings.onboarded && !settings.tutorialSeen && coach == null) {
            coach = TutorialCoach.start(binding.bottomNav) {
                settings.tutorialSeen = true
                coach = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coach?.dismiss()
        coach = null
    }

    private companion object {
        /** Matches the 180 ms of res/anim/nav_*, so the bar moves at the app's own pace. */
        const val NAV_SLIDE_MS = 180L

        /**
         * The five tabs, plus the reader — a post is where a reading trail gets deep enough
         * that popping back one screen at a time stops being a way out.
         *
         * The editor, the review screen and the Settings sub-screens stay off this list: each
         * is a task with a save or discard at the end, and a tab tap mid-edit is a lost draft.
         */
        val TABS_VISIBLE_ON = setOf(
            R.id.homeFragment,
            R.id.browseFragment,
            R.id.searchFragment,
            R.id.chatFragment,
            R.id.settingsFragment,
            R.id.postReaderFragment,
        )
    }
}
