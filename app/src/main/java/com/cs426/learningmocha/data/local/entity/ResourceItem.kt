package com.cs426.learningmocha.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ResourceType {
    YOUTUBE,
    ARTICLE,
    BOOK,
    OTHER,
}

@Entity(
    tableName = "resources",
    foreignKeys = [
        ForeignKey(
            entity = Node::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("postId")],
)
data class ResourceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postId: Long,
    val type: ResourceType,
    val title: String,
    val url: String,
)
