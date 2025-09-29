package com.atuy.scomb.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Retrofitを使ってScombZの各ページにアクセスするためのインターフェース
 */
interface ScombzApiService {
    // 課題一覧ページ
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

    // お知らせページ
    @GET("portal/home/information/list")
    suspend fun getNewsList(
        @Header("Cookie") sessionId: String
    ): Response<ResponseBody>
}
