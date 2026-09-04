package com.cs426.learningmocha.ai.protocol

data class Envelope(
    val type: String? = null,
    val text: String? = null,
    val queries: List<ContextQuery>? = null,
    val summary: String? = null,
    val actions: List<KbAction>? = null,
)

data class ContextQuery(
    val op: String? = null,
    val args: Map<String, Any?>? = null,
) {
    fun arg(key: String): String {
        val value = args?.get(key) ?: return ""
        return value.toString()
    }
}

data class KbAction(
    val op: String? = null,
    val title: String? = null,
    val ref: String? = null,
    val parentRef: String? = null,
    val parentTitle: String? = null,
    val content: String? = null,
    val tags: List<String>? = null,
    val status: String? = null,
    val postTitle: String? = null,
    val postRef: String? = null,
    val newParentTitle: String? = null,
    val fromRef: String? = null,
    val fromTitle: String? = null,
    val toTitle: String? = null,
    val tag: String? = null,
    val favorite: Boolean? = null,
    val type: String? = null,
    val url: String? = null,
    val term: String? = null,
    val definition: String? = null,
    val meaningVi: String? = null,
)
