package com.cs426.learningmocha.net

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Streaming transport for `POST /v1/chat/stream`.
 *
 * Retrofit's Gson converter buffers the whole body, so the token stream is read
 * straight from OkHttp instead. The returned flow is cold: one call per
 * collection, and cancelling the collector closes the socket.
 */
class SseChatClient(
    private val baseUrl: () -> String = { ApiClient.DEFAULT_BASE_URL },
) {
    private val gson = Gson()

    // Built on first use so a session that never opens the AI tab pays nothing.
    private val client by lazy { ApiClient.streamingClient() }

    fun stream(body: ChatRequest): Flow<StreamFrame> = flow {
        val request = Request.Builder()
            .url(ApiClient.endpoint(baseUrl(), PATH))
            .header("Accept", "text/event-stream")
            .post(gson.toJson(body).toRequestBody(JSON))
            .build()
        val call = client.newCall(request)
        // A blocked socket read does not observe coroutine cancellation, so the
        // collector going away has to close the call for that read to return.
        val cancelOnLeave = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response -> relay(response) }
        } finally {
            cancelOnLeave.dispose()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<StreamFrame>.relay(response: Response) {
        val source = response.body?.source()
        if (!response.isSuccessful || source == null) {
            emit(failure(response))
            return
        }
        while (true) {
            val line = source.readUtf8Line() ?: return
            val frame = SseFrames.parse(line) ?: continue
            emit(frame)
            // `done` and `error` are terminal; nothing useful follows them.
            if (frame !is StreamFrame.Delta) return
        }
    }

    /** A pre-stream rejection (bad mode, missing key) still arrives as normalized JSON. */
    private fun failure(response: Response): StreamFrame.Failure {
        val parsed = try {
            gson.fromJson(response.body?.string(), ChatResponse::class.java)
        } catch (_: Exception) {
            null
        }
        val code = response.code
        return StreamFrame.Failure(
            parsed?.error ?: "HTTP $code",
            parsed?.retryable ?: (code >= 500 || code == 429),
        )
    }

    private companion object {
        const val PATH = "v1/chat/stream"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
