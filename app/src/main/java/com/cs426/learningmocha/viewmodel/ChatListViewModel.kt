package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.data.local.entity.ChatSession
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatListUiState(
    val listState: ListState = ListState.LOADING,
    val sessions: List<ChatSession> = emptyList(),
    val online: Boolean = true,
    val errorMessage: String? = null,
)

class ChatListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp
    private val online = MutableStateFlow(true)

    val uiState: StateFlow<ChatListUiState> = combine(
        app.chatRepository.observeSessions(),
        online,
    ) { sessions, reachable ->
        ChatListUiState(
            listState = if (sessions.isEmpty()) ListState.EMPTY else ListState.CONTENT,
            sessions = sessions,
            online = reachable,
        )
    }.catch { error ->
        emit(ChatListUiState(listState = ListState.ERROR, errorMessage = error.message, online = false))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ChatListUiState(),
    )

    init {
        ping()
    }

    fun ping() {
        viewModelScope.launch { online.value = app.chatRepository.ping() }
    }

    fun createSession(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            onCreated(app.chatRepository.createSession())
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { app.chatRepository.deleteSession(id) }
    }
}
