package com.example.wallpaperwizard

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*

interface WallpaperApi {
    @Multipart
    @POST("/wallpaper")
    fun uploadWallpaper(
        @Part image: MultipartBody.Part,
        @Query("tags") tags: String,
        @Query("crop") crop: String
    ): Call<ResponseBody>

    @GET("/wallpaper")
    fun getWallpaper(@Query("tags") tags: String, @Query("sync") sync: String): Call<ResponseBody>

    @GET("/tags")
    fun getTags(): Call<TagsResult>


}

class API() {
    object RetrofitHelper {
        val baseUrl = "http://192.168.122.45:3000"

        fun getInstance(): Retrofit {
            return Retrofit.Builder().baseUrl(baseUrl)
                .build()
        }
    }
}

data class TagsResult(
    val tags: Array<String>
)

