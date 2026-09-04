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
                    request.newBuilder()
                        .url(
                            request.url.newBuilder()
                                .scheme(configured.scheme)
                                .host(configured.host)
                                .port(configured.port)
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
     * Absolute URL of [path] on the configured gateway. Only scheme/host/port are
     * taken from [baseUrl], which is exactly what the Retrofit interceptor above
     * does, so both transports hit the same endpoints for a given setting.
     */
    fun endpoint(baseUrl: String, path: String): HttpUrl {
        val configured = baseUrl.toHttpUrlOrNull() ?: DEFAULT_BASE_URL.toHttpUrl()
        return configured.newBuilder().encodedPath("/$path").build()
    }

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
