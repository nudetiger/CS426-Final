package com.cs426.learningmocha.net

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8787/"

    fun create(baseUrl: String = DEFAULT_BASE_URL): MochaApi {
        val http = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MochaApi::class.java)
    }
}

class ApiError(message: String, val retryable: Boolean) : Exception(message)
