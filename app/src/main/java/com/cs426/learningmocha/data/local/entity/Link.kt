package com.cs426.learningmocha.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "links",
    foreignKeys = [
        ForeignKey(
            entity = Node::class,
            parentColumns = ["id"],
            childColumns = ["fromPostId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Node::class,
            parentColumns = ["id"],
            childColumns = ["toPostId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("fromPostId"), Index("toPostId")],
)
data class Link(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromPostId: Long,
    val toPostId: Long,
    val anchorText: String,
)
