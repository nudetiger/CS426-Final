package com.cs426.learningmocha.ui.common

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.cs426.learningmocha.R

object ListStateBinder {

    fun bind(
        overlay: View,
        progress: View,
        message: TextView,
        retry: View,
        content: View,
        state: ListState,
        emptyText: String,
        errorText: String?,
        offlineText: String,
        onRetry: () -> Unit,
    ) {
        when (state) {
            ListState.CONTENT -> {
                overlay.isVisible = false
                content.isVisible = true
            }
            ListState.LOADING -> {
                overlay.isVisible = true
                content.isVisible = false
                progress.isVisible = true
                message.isVisible = false
                retry.isVisible = false
            }
            ListState.EMPTY -> {
                overlay.isVisible = true
                content.isVisible = false
                progress.isVisible = false
                message.isVisible = true
                message.setText(emptyText)
                retry.isVisible = false
            }
            ListState.ERROR -> {
                overlay.isVisible = true
                content.isVisible = false
                progress.isVisible = false
                message.isVisible = true
                message.text = errorText
                    ?: overlay.context.getString(R.string.state_error)
                retry.isVisible = true
                retry.setOnClickListener { onRetry() }
            }
            ListState.OFFLINE -> {
                overlay.isVisible = true
                content.isVisible = false
                progress.isVisible = false
                message.isVisible = true
                message.text = offlineText
                retry.isVisible = true
                retry.setOnClickListener { onRetry() }
            }
        }
    }
}
