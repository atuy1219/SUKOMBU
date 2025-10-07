package com.atuy.scomb.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ScombzApiService {

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("reg_fcm")
    suspend fun registerFcm(
        @Body fcmToken: Map<String, String>
    ): Response<StatusResponse>

    @POST("sessionid")
    suspend fun sendSessionId(
        @Body sessionId: SessionIdRequest
    ): Response<StatusResponse>

    @GET("timetable/{yearMonth}")
    suspend fun getTimetable(
        @Path("yearMonth") yearMonth: String
    ): Response<List<ApiClassCell>>

    @GET("home/{yearMonth}")
    suspend fun getHome(
        @Path("yearMonth") yearMonth: String
    ): Response<ResponseBody> // Response type is unknown

    @GET("task/{yearMonth}")
    suspend fun getTasks(
        @Path("yearMonth") yearMonth: String
    ): Response<List<ApiTask>>

    @GET("news")
    suspend fun getNews(): Response<List<ApiNewsItem>>
}

