package com.cs426.learningmocha.viewmodel

import androidx.lifecycle.ViewModel

/** Activity-scoped: Home asks Browse to open a specific folder/branch. */
class BrowseLocatorViewModel : ViewModel() {

    private var pendingParentId: Long? = null
    private var hasPending: Boolean = false

    fun requestOpen(parentId: Long) {
        pendingParentId = parentId
        hasPending = true
    }

    fun consume(): Long? {
        if (!hasPending) return null
        hasPending = false
        val id = pendingParentId
        pendingParentId = null
        return id
    }
}
