package com.cs426.learningmocha.ui.settings

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.prefs.SettingsStore
import com.cs426.learningmocha.databinding.FragmentSettingsProfileBinding
import com.cs426.learningmocha.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

class ProfileSettingsFragment : Fragment() {

    private var binding: FragmentSettingsProfileBinding? = null
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentSettingsProfileBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.profileBack.setOnClickListener { findNavController().popBackStack() }
        b.profileName.doAfterTextChanged { viewModel.setDisplayName(it?.toString().orEmpty()) }
        b.profilePhone.doAfterTextChanged { viewModel.setPhoneNumber(it?.toString().orEmpty()) }
        b.profileBirth.setOnClickListener { pickBirth() }
        b.profileGender.setOnCheckedChangeListener { _: RadioGroup, checkedId: Int ->
            viewModel.setGender(
                when (checkedId) {
                    R.id.profile_gender_male -> SettingsStore.GENDER_MALE
                    R.id.profile_gender_female -> SettingsStore.GENDER_FEMALE
                    R.id.profile_gender_other -> SettingsStore.GENDER_OTHER
                    else -> ""
                },
            )
        }
        b.profilePersonality.setOnCheckedChangeListener { _: RadioGroup, checkedId: Int ->
            viewModel.setPersonality(
                when (checkedId) {
                    R.id.profile_personality_tutor -> "tutor"
                    R.id.profile_personality_concise -> "concise"
                    R.id.profile_personality_witty -> "witty"
                    R.id.profile_personality_strict -> "strict"
                    else -> SettingsStore.PERSONALITY_WARM
                },
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (b.profileName.text?.toString() != state.displayName) {
                        b.profileName.setText(state.displayName)
                        b.profileName.setSelection(b.profileName.text?.length ?: 0)
                    }
                    if (b.profilePhone.text?.toString() != state.phoneNumber) {
                        b.profilePhone.setText(state.phoneNumber)
                        b.profilePhone.setSelection(b.profilePhone.text?.length ?: 0)
                    }
                    b.profileBirth.text = state.birthDate.ifBlank {
                        getString(R.string.settings_profile_birth_unset)
                    }
                    val genderId = when (state.gender) {
                        SettingsStore.GENDER_MALE -> R.id.profile_gender_male
                        SettingsStore.GENDER_FEMALE -> R.id.profile_gender_female
                        SettingsStore.GENDER_OTHER -> R.id.profile_gender_other
                        else -> -1
                    }
                    if (genderId != -1 && b.profileGender.checkedRadioButtonId != genderId) {
                        b.profileGender.check(genderId)
                    }
                    val personalityId = when (state.personality) {
                        "tutor" -> R.id.profile_personality_tutor
                        "concise" -> R.id.profile_personality_concise
                        "witty" -> R.id.profile_personality_witty
                        "strict" -> R.id.profile_personality_strict
                        else -> R.id.profile_personality_warm
                    }
                    if (b.profilePersonality.checkedRadioButtonId != personalityId) {
                        b.profilePersonality.check(personalityId)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun pickBirth() {
        val parts = viewModel.uiState.value.birthDate.split("-")
        val cal = Calendar.getInstance()
        val year = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.YEAR) - 18
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
        DatePickerDialog(requireContext(), { _, y, m, d ->
            viewModel.setBirthDate("%04d-%02d-%02d".format(y, m + 1, d))
        }, year, month, day).show()
    }
}
