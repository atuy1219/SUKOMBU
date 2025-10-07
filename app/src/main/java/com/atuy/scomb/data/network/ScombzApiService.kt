package com.atuy.scomb.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query


interface ScombzApiService {
    @GET("lms/task")
    suspend fun getTaskList(
        @Header("Cookie") sessionId: String
    ): Response<ResponseBody>

    @GET("lms/timetable")
    suspend fun getTimetable(
        @Header("Cookie") sessionId: String,
        @Query("year") year: Int,
        @Query("term") term: String
    ): Response<ResponseBody>

    @GET("portal/surveys/list")
    suspend fun getSurveyList(
        @Header("Cookie") sessionId: String
    ): Response<ResponseBody>

    @GET("portal/home/information/list")
    suspend fun getNewsListPage(
        @Header("Cookie") sessionId: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("portal/home/information/list/search")
    suspend fun searchNewsList(
        @Header("Cookie") sessionId: String,
        @Field("_csrf") csrfToken: String,
        @Field("viewPage") viewPage: String = "0"
    ): Response<ResponseBody>

    @GET("community/search")
    suspend fun getCommunityList(
        @Header("Cookie") sessionId: String
    ): Response<ResponseBody>
}

