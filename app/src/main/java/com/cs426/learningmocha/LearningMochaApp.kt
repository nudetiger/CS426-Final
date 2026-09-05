package com.cs426.learningmocha

import android.app.Application
import com.cs426.learningmocha.ai.chat.ChatRepository
import com.cs426.learningmocha.ai.engine.ActionExecutor
import com.cs426.learningmocha.backup.BackupReminder
import com.cs426.learningmocha.backup.BackupRepository
import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.prefs.SettingsStore
import com.cs426.learningmocha.data.repo.PostRepository
import com.cs426.learningmocha.data.repo.SearchRepository
import com.cs426.learningmocha.data.repo.TreeRepository
import com.cs426.learningmocha.net.ApiClient
import com.cs426.learningmocha.net.SseChatClient
import com.cs426.learningmocha.ui.common.AppTheme

/**
 * Learning Mocha — local-first personal learning / knowledge management app.
 *
 * Startup stays cheap: Room is created on first repository use, then seeded if empty.
 */
class LearningMochaApp : Application() {

    val settings: SettingsStore by lazy { SettingsStore(this) }

    val database: AppDatabase by lazy { AppDatabase.build(this) }

    val backupRepository: BackupRepository by lazy { BackupRepository(database) }

    val treeRepository: TreeRepository by lazy { TreeRepository(database) }

    val postRepository: PostRepository by lazy { PostRepository(database) }

    val searchRepository: SearchRepository by lazy { SearchRepository(database) }

    val actionExecutor: ActionExecutor by lazy {
        ActionExecutor(database, treeRepository, postRepository)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            db = database,
            api = ApiClient.create { settings.backendUrl },
            sse = SseChatClient { settings.backendUrl },
            search = searchRepository,
            posts = postRepository,
            executor = actionExecutor,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Theme must be applied before the first view inflates; this reads SharedPreferences
        // only, so it does not open the database and cold start stays cheap. The palette half
        // of the choice is applied by MainActivity, which is the thing that owns a window.
        AppTheme.of(settings.themeKey).applyNightMode()
        BackupReminder.createChannel(this)
        BackupReminder.notifyIfOverdue(this, settings)
    }
}
