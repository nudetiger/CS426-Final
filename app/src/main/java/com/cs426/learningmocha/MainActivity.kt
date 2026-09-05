package com.cs426.learningmocha

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
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

        val topLevel = setOf(
            R.id.homeFragment,
            R.id.browseFragment,
            R.id.searchFragment,
            R.id.chatFragment,
            R.id.settingsFragment,
        )
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility =
                if (destination.id in topLevel) View.VISIBLE else View.GONE
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
}
