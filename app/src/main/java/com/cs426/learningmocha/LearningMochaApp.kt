package com.cs426.learningmocha

import android.app.Application

/**
 * Learning Mocha — local-first personal learning / knowledge management app.
 *
 * Keeps startup cheap: Room is created lazily on first use (Phase 1), no work here.
 */
class LearningMochaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Phase 1: lazy AppDatabase + first-launch seed ("Getting Started" branch).
        // Phase 4: settings store (theme, backend URL) applied here.
    }
}
