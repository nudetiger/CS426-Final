package com.cs426.learningmocha.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8787/"

    /**
     * The gateway address the user typed, in the one form that gets stored, or null when
     * it cannot be used at all. Pure and side-effect free so it can be unit-tested.
     *
     * Blank means "back to the built-in default". A value with no scheme is read as
     * `http://` — that is what someone typing `192.168.1.5:8787` means — and callers show
     * the returned value back to the user, so the guess is never a secret. Everything else
     * OkHttp cannot parse is rejected rather than stored, because a saved-but-unreachable
     * address is indistinguishable from a dead backend.
     */
    fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return DEFAULT_BASE_URL
        val candidate = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val parsed = candidate.toHttpUrlOrNull() ?: return null
        // Retrofit and endpoint() both treat the base as a directory, so it must end in "/".
        val base = parsed.newBuilder().query(null).fragment(null).build().toString()
        return if (base.endsWith("/")) base else "$base/"
    }

    /**
     * [baseUrl] is read per request rather than captured, so changing the gateway
     * address in Settings takes effect immediately without rebuilding the client
     * (which would strand in-flight calls and any collected Flow).
     */
    fun create(baseUrl: () -> String = { DEFAULT_BASE_URL }): MochaApi {
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val configured = baseUrl().toHttpUrlOrNull()
                val routed = if (configured == null) {
                    request
                } else {
                    // Retrofit built this URL on DEFAULT_BASE_URL, whose path is only "/", so
                    // the whole path is the endpoint and can be re-hung under the configured
                    // base — which keeps any prefix the gateway lives behind.
                    request.newBuilder()
                        .url(
                            under(configured, request.url.encodedPath)
                                .encodedQuery(request.url.encodedQuery)
                                .build(),
                        )
                        .build()
                }
                chain.proceed(routed)
            }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MochaApi::class.java)
    }

    /**
     * Absolute URL of [path] on the configured gateway. Resolved the same way as the
     * Retrofit interceptor above, so both transports hit the same endpoints for a
     * given setting.
     */
    fun endpoint(baseUrl: String, path: String): HttpUrl {
        val configured = baseUrl.toHttpUrlOrNull() ?: DEFAULT_BASE_URL.toHttpUrl()
        return under(configured, path).build()
    }

    /**
     * [encodedPath] hung *under* [base] instead of replacing its path, so a gateway
     * behind a prefix (`https://host/api/`) is reachable at all. A base with no prefix
     * yields exactly the URL that replacing the path used to.
     */
    private fun under(base: HttpUrl, encodedPath: String): HttpUrl.Builder =
        base.newBuilder().addEncodedPathSegments(encodedPath.trimStart('/'))

    /**
     * Client for Server-Sent Events. The read timeout is an inter-chunk gap and
     * must outlast the gateway's own 120 s generation budget, otherwise a slow
     * first token would cancel a stream the backend is still happily filling.
     */
    fun streamingClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(130, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}

class ApiError(message: String, val retryable: Boolean) : Exception(message)
