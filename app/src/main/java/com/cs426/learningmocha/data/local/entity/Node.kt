package com.cs426.learningmocha.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NodeType {
    BRANCH,
    FOLDER,
    POST,
}

enum class LearningStatus {
    NONE,
    READING,
    IN_PROGRESS,
    FINISHED,
}

/**
 * Single-table tree: branches, folders and posts share create/rename/move/reorder/delete.
 * [content] and [status] are meaningful for posts; unused for containers.
 */
@Entity(
    tableName = "nodes",
    foreignKeys = [
        ForeignKey(
            entity = Node::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("parentId"),
        Index("title"),
        Index("updatedAt"),
        Index("type"),
    ],
)
data class Node(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val type: NodeType,
    val title: String,
    val content: String? = null,
    val status: LearningStatus = LearningStatus.NONE,
    val favorite: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
