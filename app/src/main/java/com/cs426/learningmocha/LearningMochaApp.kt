package com.cs426.learningmocha

import android.app.Application
import com.cs426.learningmocha.ai.chat.ChatRepository
import com.cs426.learningmocha.ai.engine.ActionExecutor
import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.repo.PostRepository
import com.cs426.learningmocha.data.repo.SearchRepository
import com.cs426.learningmocha.data.repo.TreeRepository
import com.cs426.learningmocha.net.ApiClient

/**
 * Learning Mocha — local-first personal learning / knowledge management app.
 *
 * Startup stays cheap: Room is created on first repository use, then seeded if empty.
 */
class LearningMochaApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }

    val treeRepository: TreeRepository by lazy { TreeRepository(database) }

    val postRepository: PostRepository by lazy { PostRepository(database) }

    val searchRepository: SearchRepository by lazy { SearchRepository(database) }

    val actionExecutor: ActionExecutor by lazy {
        ActionExecutor(database, treeRepository, postRepository)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            db = database,
            api = ApiClient.create(),
            search = searchRepository,
            posts = postRepository,
            executor = actionExecutor,
        )
    }
}
