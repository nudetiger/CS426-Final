package com.cs426.learningmocha.data.repo

import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.local.SeedData
import com.cs426.learningmocha.data.local.entity.LearningStatus
import com.cs426.learningmocha.data.local.entity.Node
import com.cs426.learningmocha.data.local.entity.NodeType
import com.cs426.learningmocha.util.FtsQueryBuilder

data class SearchHit(
    val id: Long,
    val title: String,
    val caption: String,
    val kind: Kind,
    val postId: Long?,
) {
    enum class Kind { POST, BRANCH, DICTIONARY }
}

data class SearchFilter(
    val query: String = "",
    val type: NodeType? = null,
    val status: LearningStatus? = null,
    val favoritesOnly: Boolean = false,
)

class SearchRepository(private val db: AppDatabase) {

    private val dao = db.nodeDao()
    private val knowledge = db.knowledgeDao()

    suspend fun search(filter: SearchFilter): List<SearchHit> {
        SeedData.ensureSeeded(db)
        val q = filter.query.trim()
        val hasQuery = q.isNotEmpty()
        val hasFilter = filter.favoritesOnly || filter.status != null || filter.type != null
        if (!hasQuery && !hasFilter) return emptyList()

        val hits = LinkedHashMap<String, SearchHit>()
        fun put(hit: SearchHit) {
            hits.putIfAbsent("${hit.kind}:${hit.id}", hit)
        }

        val match = FtsQueryBuilder.toMatchQuery(q)
        if (match != null) {
            knowledge.fts(match).forEach { node ->
                if (passes(node, filter)) put(node.toHit())
            }
            val like = likePattern(q)
            dao.titlesLike(like).forEach { node ->
                if (passes(node, filter)) put(node.toHit())
            }
            knowledge.tagsNamedLike(like).forEach { tag ->
                knowledge.postsWithTag(tag.id).forEach { node ->
                    if (passes(node, filter)) put(node.toHit())
                }
            }
            if (filter.type == null && !filter.favoritesOnly && filter.status == null) {
                knowledge.searchDictionary(like).forEach { entry ->
                    put(
                        SearchHit(
                            id = entry.id,
                            title = entry.term,
                            caption = entry.definition,
                            kind = SearchHit.Kind.DICTIONARY,
                            postId = entry.postId,
                        ),
                    )
                }
            }
        } else if (hasFilter) {
            dao.getAll().forEach { node ->
                if ((node.type == NodeType.POST || node.type == NodeType.BRANCH) && passes(node, filter)) {
                    put(node.toHit())
                }
            }
        }

        return hits.values.sortedWith(
            compareBy<SearchHit> { it.kind != SearchHit.Kind.POST }
                .thenBy { !it.title.startsWith(q, ignoreCase = true) }
                .thenBy { it.title.lowercase() },
        ).take(50)
    }

    private fun passes(node: Node, filter: SearchFilter): Boolean {
        if (filter.type != null && node.type != filter.type) return false
        if (filter.status != null && (node.type != NodeType.POST || node.status != filter.status)) {
            return false
        }
        if (filter.favoritesOnly && (node.type != NodeType.POST || !node.favorite)) return false
        if (filter.type == null && node.type != NodeType.POST && node.type != NodeType.BRANCH) {
            return false
        }
        return true
    }

    private fun Node.toHit(): SearchHit {
        val kind = if (type == NodeType.BRANCH) SearchHit.Kind.BRANCH else SearchHit.Kind.POST
        return SearchHit(
            id = id,
            title = title,
            caption = if (type == NodeType.BRANCH) "Branch" else "Post",
            kind = kind,
            postId = if (type == NodeType.POST) id else null,
        )
    }

    private fun likePattern(raw: String): String =
        "%" + raw.replace("%", "").replace("_", "") + "%"
}
