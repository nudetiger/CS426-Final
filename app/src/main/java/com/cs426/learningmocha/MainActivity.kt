package com.cs426.learningmocha

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.cs426.learningmocha.databinding.ActivityMainBinding

/**
 * Single-activity shell: NavHost + bottom navigation with the five top-level tabs
 * (Home, Browse, Search, AI, Settings). Reader/editor sit on the back stack and hide the tabs.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        }
    }
}
