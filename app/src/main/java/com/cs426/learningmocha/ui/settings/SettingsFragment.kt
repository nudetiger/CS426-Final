package com.cs426.learningmocha.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.FragmentSettingsBinding
import com.cs426.learningmocha.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The settings hub: four categories, each its own screen.
 *
 * Everything used to sit on one scroll, which put the theme radio a thumb away from Import —
 * the one control here that can replace the whole library. Splitting them also leaves room for
 * settings to explain themselves rather than fighting for vertical space.
 */
class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentSettingsBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.settingsOpenAppearance.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_appearance)
        }
        b.settingsOpenProfile.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_profile)
        }
        b.settingsOpenAi.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_ai)
        }
        b.settingsOpenBackup.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_backup)
        }
        b.settingsOpenAbout.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_about)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // The one live value on this screen: how long it has been since an export is
                // the reason someone opens Backup, so it belongs on the row that leads there.
                viewModel.uiState.collect { state ->
                    b.settingsBackupHint.text = if (state.lastBackupAt == 0L) {
                        getString(R.string.settings_backup_never)
                    } else {
                        getString(
                            R.string.settings_backup_last,
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(state.lastBackupAt)),
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
