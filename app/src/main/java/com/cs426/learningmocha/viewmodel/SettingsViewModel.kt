package com.cs426.learningmocha.viewmodel

import android.app.Application
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.R
import com.cs426.learningmocha.backup.BackupReminder
import com.cs426.learningmocha.backup.BackupSnapshot
import com.cs426.learningmocha.net.ApiClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    val backendUrl: String = "",
    val remindersEnabled: Boolean = true,
    val lastBackupAt: Long = 0L,
    val busy: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.UNKNOWN,
)

enum class ConnectionStatus { UNKNOWN, TESTING, ONLINE, OFFLINE }

/** A parsed backup waiting for the user to choose merge or replace. */
data class PendingImport(val snapshot: BackupSnapshot)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp
    private val settings = app.settings

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = settings.themeMode,
            backendUrl = settings.backendUrl,
            remindersEnabled = settings.backupRemindersEnabled,
            lastBackupAt = settings.lastBackupAt,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState

    /** One-shot user feedback; the fragment shows it as a snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val importPrompts = MutableSharedFlow<PendingImport>(extraBufferCapacity = 1)

    fun setThemeMode(mode: Int) {
        if (mode == settings.themeMode) return
        settings.themeMode = mode
        settings.applyTheme()
        _uiState.update { it.copy(themeMode = mode) }
    }

    /**
     * Stores [raw] if it can be used as a gateway address, and reports whether it was.
     * A rejected value is said out loud instead of being quietly dropped — an address the
     * app cannot parse otherwise looks accepted while every request keeps failing.
     */
    fun setBackendUrl(raw: String): Boolean {
        val normalized = ApiClient.normalizeBaseUrl(raw)
        if (normalized == null) {
            viewModelScope.launch { messages.emit(string(R.string.settings_backend_invalid)) }
            return false
        }
        settings.backendUrl = normalized
        _uiState.update {
            it.copy(backendUrl = settings.backendUrl, connectionStatus = ConnectionStatus.UNKNOWN)
        }
        return true
    }

    fun resetBackendUrl(): Boolean = setBackendUrl("")

    fun setRemindersEnabled(enabled: Boolean) {
        settings.backupRemindersEnabled = enabled
        if (!enabled) BackupReminder.clear(getApplication())
        _uiState.update { it.copy(remindersEnabled = enabled) }
    }

    fun testConnection() {
        if (_uiState.value.connectionStatus == ConnectionStatus.TESTING) return
        viewModelScope.launch {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.TESTING) }
            val ok = app.chatRepository.ping()
            _uiState.update {
                it.copy(
                    connectionStatus =
                    if (ok) ConnectionStatus.ONLINE else ConnectionStatus.OFFLINE,
                )
            }
        }
    }

    fun export(target: Uri) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                val stream = getApplication<Application>().contentResolver.openOutputStream(target)
                    ?: error(string(R.string.settings_export_no_access))
                val posts = stream.use { app.backupRepository.export(it) }
                settings.lastBackupAt = System.currentTimeMillis()
                settings.reminderClockAt = settings.lastBackupAt
                BackupReminder.clear(getApplication())
                _uiState.update { it.copy(lastBackupAt = settings.lastBackupAt) }
                messages.emit(string(R.string.settings_export_done, posts))
            } catch (error: Exception) {
                messages.emit(string(R.string.settings_export_failed, reason(error)))
            }
            _uiState.update { it.copy(busy = false) }
        }
    }

    /** Parses and validates the file, then asks the fragment for merge-or-replace. */
    fun stageImport(source: Uri) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                val stream = getApplication<Application>().contentResolver.openInputStream(source)
                    ?: error(string(R.string.settings_export_no_access))
                val snapshot = stream.use { app.backupRepository.peek(it) }
                if (snapshot.isEmpty) {
                    messages.emit(string(R.string.settings_import_empty))
                } else {
                    importPrompts.emit(PendingImport(snapshot))
                }
            } catch (error: Exception) {
                messages.emit(string(R.string.settings_import_failed, reason(error)))
            }
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun applyImport(pending: PendingImport, replace: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                val posts = app.backupRepository.restore(pending.snapshot, replace)
                messages.emit(string(R.string.settings_import_done, posts))
            } catch (error: Exception) {
                messages.emit(string(R.string.settings_import_failed, reason(error)))
            }
            _uiState.update { it.copy(busy = false) }
        }
    }

    private fun reason(error: Exception): String =
        error.message?.takeIf { it.isNotBlank() } ?: string(R.string.settings_unknown_error)

    private fun string(res: Int, vararg args: Any): String =
        getApplication<Application>().getString(res, *args)
}
