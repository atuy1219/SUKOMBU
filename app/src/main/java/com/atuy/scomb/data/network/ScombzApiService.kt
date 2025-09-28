package com.atuy.scomb.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Retrofitを使ってScombZの各ページにアクセスするためのインターフェース
 */
interface ScombzApiService {
    // 課題一覧ページを取得
    @GET("lms/task")
    suspend fun getTaskList(
        @Header("Cookie") sessionId: String // "SESSION=xxxx" の形で渡す
    ): Response<ResponseBody> // レスポンスはHTMLなのでResponseBodyで受け取る

    // 他のページ（時間割、お知らせなど）も同様に追加していく
    // @GET("lms/timetable?risyunen=...")
    // suspend fun getTimetable(...)
}