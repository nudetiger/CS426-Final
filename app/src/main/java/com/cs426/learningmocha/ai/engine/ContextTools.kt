package com.cs426.learningmocha.ai.engine

import com.cs426.learningmocha.ai.protocol.ContextQuery
import com.cs426.learningmocha.data.local.AppDatabase
import com.cs426.learningmocha.data.repo.PostRepository
import com.cs426.learningmocha.data.repo.SearchFilter
import com.cs426.learningmocha.data.repo.SearchRepository
import com.google.gson.Gson

class ContextTools(
    private val db: AppDatabase,
    private val search: SearchRepository,
    private val posts: PostRepository,
) {
    private val gson = Gson()

    suspend fun run(queries: List<ContextQuery>): String {
        val results = queries.take(8).map { query ->
            mapOf("op" to query.op, "result" to execute(query).take(2_000))
        }
        return gson.toJson(results)
    }

    private suspend fun execute(query: ContextQuery): String {
        val nodes = db.nodeDao()
        val knowledge = db.knowledgeDao()
        return when (query.op) {
            "search_posts" -> {
                val hits = search.search(SearchFilter(query = query.arg("query")))
                hits.take(10).joinToString("\n") { "${it.kind}: ${it.title}" }
            }
            "get_post" -> {
                val node = nodes.findPostByTitle(query.arg("title"))
                    ?: return "not found"
                val tags = knowledge.tagsForPost(node.id).joinToString { it.name }
                buildString {
                    append("# ").append(node.title).append('\n')
                    if (tags.isNotEmpty()) append("tags: ").append(tags).append('\n')
                    append(node.content.orEmpty())
                }
            }
            "list_children" -> {
                val parentTitle = query.arg("parentTitle")
                val kids = if (parentTitle.isBlank()) {
                    nodes.getRoots()
                } else {
                    val parent = nodes.findByTitle(parentTitle) ?: return "parent not found"
                    nodes.getChildren(parent.id)
                }
                kids.joinToString("\n") { "${it.type.name.lowercase()}: ${it.title}" }
            }
            "get_backlinks" -> {
                val node = nodes.findPostByTitle(query.arg("title")) ?: return "not found"
                knowledge.backlinks(node.id).joinToString("\n") { it.title }
            }
            "search_dictionary" -> {
                val like = "%" + query.arg("query").replace("%", "").replace("_", "") + "%"
                knowledge.searchDictionary(like).take(15)
                    .joinToString("\n") { "${it.term}: ${it.definition}" }
            }
            "get_tags" -> knowledge.allTags().joinToString("\n") { it.name }
            "get_related" -> {
                val node = nodes.findPostByTitle(query.arg("title")) ?: return "not found"
                posts.related(node.id).joinToString("\n") { it.title }
            }
            else -> "unknown op"
        }
    }
}
