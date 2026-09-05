package com.cs426.learningmocha.ui.settings

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cs426.learningmocha.R
import com.cs426.learningmocha.backup.BackupReminder
import com.cs426.learningmocha.databinding.DialogDeleteEverythingBinding
import com.cs426.learningmocha.databinding.FragmentSettingsBackupBinding
import com.cs426.learningmocha.ui.common.themeColor
import com.cs426.learningmocha.viewmodel.PendingImport
import com.cs426.learningmocha.viewmodel.SettingsUiState
import com.cs426.learningmocha.viewmodel.SettingsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Export, import and the weekly reminder — the screen that can replace the whole library. */
class BackupSettingsFragment : Fragment() {

    private var binding: FragmentSettingsBackupBinding? = null
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
        val view = FragmentSettingsBackupBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.backupBack.setOnClickListener { findNavController().popBackStack() }
        b.settingsExport.setOnClickListener { createBackup.launch(defaultFileName()) }
        b.settingsImport.setOnClickListener { openBackup.launch(arrayOf("application/json", "*/*")) }
        b.settingsReset.setOnClickListener { askDeleteEverything() }
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
                launch { viewModel.resetDone.collect { restartApp() } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(state: SettingsUiState) {
        val b = binding ?: return
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
        b.settingsReset.isEnabled = !state.busy
    }

    private fun askMergeOrReplace(pending: PendingImport) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_import_title)
            .setMessage(
                resources.getQuantityString(
                    R.plurals.settings_import_message,
                    pending.snapshot.postCount,
                    pending.snapshot.postCount,
                ),
            )
            .setPositiveButton(R.string.settings_import_merge) { _, _ ->
                viewModel.applyImport(pending, replace = false)
            }
            .setNegativeButton(R.string.settings_import_replace) { _, _ ->
                viewModel.applyImport(pending, replace = true)
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * The second ask. Both a confirmation and a typed keyword, because the button behind this
     * dialog destroys more than any other control in the app: Delete stays disabled until the
     * box holds exactly the word, so nothing here can be dismissed by reflex.
     */
    private fun askDeleteEverything() {
        val fields = DialogDeleteEverythingBinding.inflate(layoutInflater)
        val keyword = getString(R.string.settings_reset_keyword)
        fields.resetInstruction.text = getString(R.string.settings_reset_type, keyword)
        fields.resetKeyword.hint = getString(R.string.settings_reset_hint, keyword)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_reset_confirm_title)
            .setView(fields.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_reset) { _, _ -> viewModel.deleteEverything() }
            .create()
        dialog.setOnShowListener {
            val confirm = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            confirm.isEnabled = false
            // Red when it will act, faded when it will not. A flat colour here made a dead
            // button look live, which is exactly the wrong signal on this dialog.
            val error = requireContext().themeColor(R.attr.mochaError)
            confirm.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(error, ColorUtils.setAlphaComponent(error, DISABLED_ALPHA)),
                ),
            )
            fields.resetKeyword.doAfterTextChanged { typed ->
                confirm.isEnabled = typed?.toString()?.trim() == keyword
            }
        }
        dialog.show()
    }

    /**
     * Relaunches the task from scratch. The reset cleared the preferences the running process
     * had already read — theme, profile, the onboarding flag — so restarting is what actually
     * puts the user back at a first launch instead of a half-stale one.
     */
    private fun restartApp() {
        val context = requireContext().applicationContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        activity?.finish()
    }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !BackupReminder.canPost(requireContext())

    private fun defaultFileName(): String {
        val stamp = android.text.format.DateFormat.format("yyyy-MM-dd", Date())
        return "learning-mocha-$stamp.mocha.json"
    }

    private companion object {
        /** Material's disabled-text opacity, as an alpha byte. */
        const val DISABLED_ALPHA = 0x3D
    }

    private fun snack(text: String) {
        val b = binding ?: return
        Snackbar.make(b.root, text, Snackbar.LENGTH_LONG).show()
    }
}
