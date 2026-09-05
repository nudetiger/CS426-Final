package com.cs426.learningmocha.ui.onboarding

import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.prefs.SettingsStore
import com.cs426.learningmocha.databinding.ActivityOnboardingBinding
import com.cs426.learningmocha.ui.common.AppTheme
import java.util.Calendar

/**
 * First launch: name, phone, birth year and the two preferences that shape how Mocha writes.
 * Everything collected here lands in [SettingsStore] and shows up again, editable, on
 * Settings -> You; nothing is asked twice.
 *
 * It is an Activity rather than a destination in the nav graph because it has no bottom
 * navigation and no back stack of its own — MainActivity launches it and waits behind it.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val settings: SettingsStore by lazy { (application as LearningMochaApp).settings }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppTheme.of(settings.themeKey).styleRes)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
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

        binding.onboardingStart.setOnClickListener { finishSetup() }

        // Back must not walk past setup. Finishing this activity alone would reveal a Home
        // screen with no profile behind it and leave the welcome flow queued up for the next
        // launch, so back leaves the app instead — the way a first-run screen usually behaves.
        onBackPressedDispatcher.addCallback(this) { finishAffinity() }
    }

    /**
     * The form is never blocked: the button always responds, and an incomplete form says which
     * three fields are still needed rather than sitting greyed out with no explanation.
     */
    private fun finishSetup() {
        val name = binding.onboardingName.text?.toString()?.trim().orEmpty()
        val phone = binding.onboardingPhone.text?.toString()?.trim().orEmpty()
        val year = binding.onboardingBirthYear.text?.toString()?.trim().orEmpty()
        if (name.isEmpty() || phone.isEmpty() || !isPlausibleYear(year)) {
            Toast.makeText(this, R.string.onboarding_incomplete, Toast.LENGTH_SHORT).show()
            return
        }
        settings.displayName = name
        settings.phoneNumber = phone
        // A bare year, which is all this screen asks for. The profile screen can refine it into
        // a full date later; SettingsStore.ageYears reads either shape.
        settings.birthDate = year
        settings.gender = when (binding.onboardingGender.checkedRadioButtonId) {
            R.id.onboarding_gender_male -> SettingsStore.GENDER_MALE
            R.id.onboarding_gender_female -> SettingsStore.GENDER_FEMALE
            R.id.onboarding_gender_other -> SettingsStore.GENDER_OTHER
            else -> ""
        }
        settings.personality = when (binding.onboardingPersonality.checkedRadioButtonId) {
            R.id.onboarding_personality_tutor -> "tutor"
            R.id.onboarding_personality_concise -> "concise"
            R.id.onboarding_personality_witty -> "witty"
            R.id.onboarding_personality_strict -> "strict"
            else -> SettingsStore.PERSONALITY_WARM
        }
        settings.onboarded = true
        finish()
    }

    private fun isPlausibleYear(raw: String): Boolean {
        val year = raw.toIntOrNull() ?: return false
        return year in 1900..Calendar.getInstance().get(Calendar.YEAR)
    }
}
