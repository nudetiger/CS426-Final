package com.cs426.learningmocha.ai.protocol

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

object ActionParser {
    private val gson = Gson()
    private val fenced = Regex("^```(?:json)?\\s*([\\s\\S]*?)```$", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Envelope {
        val json = extractJson(raw)
        val envelope = try {
            gson.fromJson(json, Envelope::class.java)
        } catch (error: JsonSyntaxException) {
            throw IllegalArgumentException("Reply is not a JSON envelope", error)
        } ?: throw IllegalArgumentException("Envelope is missing type")
        if (envelope.type.isNullOrBlank()) {
            throw IllegalArgumentException("Envelope is missing type")
        }
        return envelope
    }

    fun parseOrAnswer(raw: String): Envelope {
        return try {
            parse(raw)
        } catch (_: Exception) {
            Envelope(type = "answer", text = raw)
        }
    }

    internal fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        val fencedMatch = fenced.find(trimmed)
        val body = fencedMatch?.groupValues?.get(1)?.trim() ?: trimmed
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        return if (start >= 0 && end > start) body.substring(start, end + 1) else body
    }
}
