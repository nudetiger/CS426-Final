package com.cs426.learningmocha

import android.app.Application
import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.repo.PostRepository
import com.cs426.learningmocha.data.repo.TreeRepository

/**
 * Learning Mocha — local-first personal learning / knowledge management app.
 *
 * Startup stays cheap: Room is created on first repository use, then seeded if empty.
 */
class LearningMochaApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }

    val treeRepository: TreeRepository by lazy { TreeRepository(database) }

    val postRepository: PostRepository by lazy { PostRepository(database) }
}
