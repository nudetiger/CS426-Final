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
import com.cs426.learningmocha.databinding.FragmentSettingsAiBinding
import com.cs426.learningmocha.viewmodel.ConnectionStatus
import com.cs426.learningmocha.viewmodel.SettingsUiState
import com.cs426.learningmocha.viewmodel.SettingsViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Where the gateway lives, and how much the assistant is allowed to interrupt. */
class AiSettingsFragment : Fragment() {

    private var binding: FragmentSettingsAiBinding? = null
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = FragmentSettingsAiBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        b.aiBack.setOnClickListener { findNavController().popBackStack() }

        b.settingsBackendSave.setOnClickListener {
            if (viewModel.setBackendUrl(b.settingsBackendUrl.text.toString())) {
                showStoredBackendUrl()
                snack(getString(R.string.settings_backend_saved))
            }
        }
        b.settingsBackendReset.setOnClickListener {
            viewModel.resetBackendUrl()
            showStoredBackendUrl()
        }
        b.settingsBackendTest.setOnClickListener {
            if (viewModel.setBackendUrl(b.settingsBackendUrl.text.toString())) {
                showStoredBackendUrl()
                viewModel.testConnection()
            }
        }
        b.settingsSuggestMode.setOnCheckedChangeListener { button, checked ->
            if (button.isPressed) viewModel.setSuggestChatMode(checked)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch { viewModel.messages.collect { snack(it) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(state: SettingsUiState) {
        val b = binding ?: return
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
        b.settingsBackendTest.isEnabled = state.connectionStatus != ConnectionStatus.TESTING
        if (b.settingsSuggestMode.isChecked != state.suggestChatMode) {
            b.settingsSuggestMode.isChecked = state.suggestChatMode
        }
    }

    /**
     * The address is normalized on the way in (a missing scheme, a missing trailing slash),
     * so show what was actually stored. [render] cannot: the field still holds focus right
     * after the tap, and it leaves a focused field alone so typing is never overwritten.
     */
    private fun showStoredBackendUrl() {
        val b = binding ?: return
        b.settingsBackendUrl.setText(viewModel.uiState.value.backendUrl)
    }

    private fun snack(text: String) {
        val b = binding ?: return
        Snackbar.make(b.root, text, Snackbar.LENGTH_LONG).show()
    }
}
