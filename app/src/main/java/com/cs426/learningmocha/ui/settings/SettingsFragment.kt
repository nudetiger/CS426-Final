package com.cs426.learningmocha.ui.settings

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cs426.learningmocha.R
import com.cs426.learningmocha.databinding.FragmentSettingsBinding
import com.cs426.learningmocha.viewmodel.ConnectionStatus
import com.cs426.learningmocha.viewmodel.PendingImport
import com.cs426.learningmocha.viewmodel.SettingsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Theme, AI gateway address, backup / export / import, privacy. */
class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null
    private val viewModel: SettingsViewModel by viewModels()

    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.export(it) } }

    private val openBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.stageImport(it) } }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setRemindersEnabled(granted) }

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

        b.settingsThemeGroup.setOnCheckedChangeListener { _, checkedId ->
            viewModel.setThemeMode(
                when (checkedId) {
                    R.id.settings_theme_light -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.settings_theme_dark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                },
            )
        }
        b.settingsBackendSave.setOnClickListener {
            viewModel.setBackendUrl(b.settingsBackendUrl.text.toString())
            snack(getString(R.string.settings_backend_saved))
        }
        b.settingsBackendReset.setOnClickListener { viewModel.resetBackendUrl() }
        b.settingsBackendTest.setOnClickListener {
            viewModel.setBackendUrl(b.settingsBackendUrl.text.toString())
            viewModel.testConnection()
        }
        b.settingsExport.setOnClickListener { createBackup.launch(defaultFileName()) }
        b.settingsImport.setOnClickListener { openBackup.launch(arrayOf("application/json", "*/*")) }
        b.settingsBackupReminder.setOnCheckedChangeListener { button, checked ->
            if (!button.isPressed) return@setOnCheckedChangeListener
            if (checked && needsNotificationPermission()) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setRemindersEnabled(checked)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch { viewModel.messages.collect { snack(it) } }
                launch { viewModel.importPrompts.collect { askMergeOrReplace(it) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(state: com.cs426.learningmocha.viewmodel.SettingsUiState) {
        val b = binding ?: return
        val target = when (state.themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.settings_theme_light
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.settings_theme_dark
            else -> R.id.settings_theme_system
        }
        if (b.settingsThemeGroup.checkedRadioButtonId != target) {
            b.settingsThemeGroup.check(target)
        }
        if (!b.settingsBackendUrl.hasFocus() &&
            b.settingsBackendUrl.text.toString() != state.backendUrl
        ) {
            b.settingsBackendUrl.setText(state.backendUrl)
        }
        b.settingsBackendStatus.setText(
            when (state.connectionStatus) {
                ConnectionStatus.UNKNOWN -> R.string.settings_backend_help
                ConnectionStatus.TESTING -> R.string.settings_backend_testing
                ConnectionStatus.ONLINE -> R.string.settings_backend_ok
                ConnectionStatus.OFFLINE -> R.string.settings_backend_failed
            },
        )
        b.settingsBackupStatus.text = if (state.lastBackupAt == 0L) {
            getString(R.string.settings_backup_never)
        } else {
            getString(
                R.string.settings_backup_last,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(state.lastBackupAt)),
            )
        }
        if (b.settingsBackupReminder.isChecked != state.remindersEnabled) {
            b.settingsBackupReminder.isChecked = state.remindersEnabled
        }
        b.settingsExport.isEnabled = !state.busy
        b.settingsImport.isEnabled = !state.busy
        b.settingsBackendTest.isEnabled = state.connectionStatus != ConnectionStatus.TESTING
    }

    private fun askMergeOrReplace(pending: PendingImport) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_import_title)
            .setMessage(getString(R.string.settings_import_message, pending.snapshot.postCount))
            .setPositiveButton(R.string.settings_import_merge) { _, _ ->
                viewModel.applyImport(pending, replace = false)
            }
            .setNegativeButton(R.string.settings_import_replace) { _, _ ->
                viewModel.applyImport(pending, replace = true)
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !com.cs426.learningmocha.backup.BackupReminder.canPost(requireContext())

    private fun defaultFileName(): String {
        val stamp = android.text.format.DateFormat.format("yyyy-MM-dd", Date())
        return "learning-mocha-$stamp.mocha.json"
    }

    private fun snack(text: String) {
        val b = binding ?: return
        Snackbar.make(b.root, text, Snackbar.LENGTH_LONG).show()
    }
}
