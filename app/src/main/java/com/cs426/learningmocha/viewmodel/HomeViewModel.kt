package com.cs426.learningmocha.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs426.learningmocha.LearningMochaApp
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.ui.common.ListState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class HomeUiState(
    val listState: ListState = ListState.LOADING,
    val greetingRes: Int = R.string.home_greeting_evening,
    val postCount: Int = 0,
    val continueReading: List<Node> = emptyList(),
    val recents: List<Node> = emptyList(),
    val favorites: List<Node> = emptyList(),
    val branches: List<Node> = emptyList(),
    val errorMessage: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LearningMochaApp
    private val greetingRes = greetingRes()

    val uiState: StateFlow<HomeUiState> = combine(
        app.postRepository.observeContinueReading(8),
        app.postRepository.observeRecentPosts(12),
        app.postRepository.observeFavorites(),
        app.treeRepository.observeRootBranches(),
        app.postRepository.observePostCount(),
    ) { continueReading, recents, favorites, branches, postCount ->
        val empty = continueReading.isEmpty() && recents.isEmpty() &&
            favorites.isEmpty() && branches.isEmpty()
        HomeUiState(
            listState = if (empty) ListState.EMPTY else ListState.CONTENT,
            greetingRes = greetingRes,
            postCount = postCount,
            continueReading = continueReading,
            recents = recents,
            favorites = favorites,
            branches = branches,
        )
    }.catch { error ->
        emit(
            HomeUiState(
                listState = ListState.ERROR,
                greetingRes = greetingRes,
                errorMessage = error.message,
            ),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(greetingRes = greetingRes),
    )

    private fun greetingRes(): Int {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        }
    }
}
