package com.cs426.learningmocha.net

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface MochaApi {
    @GET("v1/health")
    suspend fun health(): HealthResponse

    @POST("v1/chat")
    suspend fun chat(@Body body: ChatRequest): Response<ChatResponse>
}
