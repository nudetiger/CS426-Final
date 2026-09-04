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
    enum class Kind { POST, BRANCH, DICTIONARY, RESOURCE }
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
                if (passes(node, filter)) put(node.toHit(q))
            }
            val like = likePattern(q)
            dao.titlesLike(like).forEach { node ->
                if (passes(node, filter)) put(node.toHit(q))
            }
            knowledge.tagsNamedLike(like).forEach { tag ->
                knowledge.postsWithTag(tag.id).forEach { node ->
                    if (passes(node, filter)) put(node.toHit(q))
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
                knowledge.searchResources(like).forEach { item ->
                    put(
                        SearchHit(
                            id = item.id,
                            title = item.title.ifBlank { item.url },
                            caption = item.url,
                            kind = SearchHit.Kind.RESOURCE,
                            postId = item.postId,
                        ),
                    )
                }
            }
        } else if (hasFilter) {
            dao.getAll().forEach { node ->
                if (passes(node, filter)) put(node.toHit(q))
            }
        }

        return hits.values.sortedWith(
            compareBy<SearchHit> { it.kind != SearchHit.Kind.POST }
                .thenBy { !it.title.startsWith(q, ignoreCase = true) }
                .thenBy { it.title.lowercase() },
        ).take(50)
    }

    private fun passes(node: Node, filter: SearchFilter): Boolean {
        // The type chip reads "Branches & folders", so a folder passes the container filter.
        val typeOk = filter.type == null ||
            node.type == filter.type ||
            (filter.type == NodeType.BRANCH && node.type == NodeType.FOLDER)
        if (!typeOk) return false
        if (filter.status != null && (node.type != NodeType.POST || node.status != filter.status)) {
            return false
        }
        if (filter.favoritesOnly && (node.type != NodeType.POST || !node.favorite)) return false
        return true
    }

    /** Containers keep `postId = null`: opening one belongs in Browse, not the reader. */
    private fun Node.toHit(query: String): SearchHit {
        val container = type != NodeType.POST
        return SearchHit(
            id = id,
            title = title,
            caption = when {
                container -> if (type == NodeType.BRANCH) "Branch" else "Folder"
                title.contains(query.trim(), ignoreCase = true) -> "Post"
                else -> snippet(content.orEmpty(), query) ?: "Post"
            },
            kind = if (container) SearchHit.Kind.BRANCH else SearchHit.Kind.POST,
            postId = if (container) null else id,
        )
    }

    /** Body excerpt around the first match, so a hit that matched only the article says why. */
    private fun snippet(content: String, query: String): String? {
        val token = query.trim().substringBefore(' ')
        if (token.isEmpty()) return null
        val at = content.indexOf(token, ignoreCase = true)
        if (at < 0) return null
        var start = (at - 30).coerceAtLeast(0)
        var end = (at + token.length + 60).coerceAtMost(content.length)
        while (start > 0 && !content[start - 1].isWhitespace()) start--
        while (end < content.length && !content[end].isWhitespace()) end++
        val text = content.substring(start, end).replace(WHITESPACE, " ").trim()
        if (text.isEmpty()) return null
        val head = if (start > 0) "…" else ""
        val tail = if (end < content.length) "…" else ""
        return head + text + tail
    }

    private fun likePattern(raw: String): String =
        "%" + raw.replace("%", "").replace("_", "") + "%"

    private companion object {
        val WHITESPACE = Regex("""\s+""")
    }
}
