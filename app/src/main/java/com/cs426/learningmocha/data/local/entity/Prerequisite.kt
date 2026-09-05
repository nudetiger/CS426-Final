package com.cs426.learningmocha.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * "[postId] should be read after [requiresId]" — a learning order the user declares by hand or
 * accepts from the assistant.
 *
 * Deliberately not folded into `links`. That table is *derived*: `KnowledgeSync.reindex`
 * rebuilds it from a post's markdown on every save, so a prerequisite stored there would be
 * deleted the next time its post was edited. This one is authored, and only ever written by
 * someone asking for it.
 *
 * The pair is the primary key, so declaring the same prerequisite twice is a no-op rather than
 * a duplicate row. Both ends cascade: deleting either post removes the edge, never leaving a
 * requirement pointing at nothing.
 */
@Entity(
    tableName = "prerequisites",
    primaryKeys = ["postId", "requiresId"],
    foreignKeys = [
        ForeignKey(
            entity = Node::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Node::class,
            parentColumns = ["id"],
            childColumns = ["requiresId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // postId is covered by the composite key's leading column; this answers the other
    // direction — "what is waiting on this post?" — which the graph and delete both ask.
    indices = [Index("requiresId")],
)
data class Prerequisite(
    val postId: Long,
    val requiresId: Long,
)
