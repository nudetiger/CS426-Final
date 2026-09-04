package com.cs426.learningmocha.ui.common

import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.local.entity.LearningStatus

fun LearningStatus.labelRes(): Int = when (this) {
    LearningStatus.NONE -> R.string.status_none
    LearningStatus.READING -> R.string.status_reading
    LearningStatus.IN_PROGRESS -> R.string.status_in_progress
    LearningStatus.FINISHED -> R.string.status_finished
}
