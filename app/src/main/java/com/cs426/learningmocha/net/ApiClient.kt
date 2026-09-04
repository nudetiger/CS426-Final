package com.cs426.learningmocha.net

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
}

class ApiError(message: String, val retryable: Boolean) : Exception(message)
