package com.cs426.learningmocha.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = Node::class)
@Entity(tableName = "posts_fts")
data class PostFts(
    val title: String,
    val content: String?,
)
