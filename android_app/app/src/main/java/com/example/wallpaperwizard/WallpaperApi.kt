package com.example.wallpaperwizard

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface WallpaperApi {
    @Multipart
    @POST("/wallpaper")
    fun uploadWallpaper(
        @Part image: MultipartBody.Part, @Query("tags") tags: String, @Query("crop") crop: String
    ): Call<ResponseBody>

    @PUT("/wallpaper/{wallpaper_name}")
    fun updateWallpaper(
        @Path("wallpaper_name") wallpaper_name: String,
        @Query("tags") tags: String,
        @Query("crop") crop: String
    ): Call<ResponseBody>

    @DELETE("/wallpaper/{wallpaper_name}")
    fun deleteWallpaper(
        @Path("wallpaper_name") wallpaper_name: String
    ): Call<ResponseBody>

    @GET("/wallpaper")
    fun getWallpaper(@Query("tags") tags: String, @Query("sync") sync: String): Call<ResponseBody>

    @GET("/thumbnail/{wallpaper_name}")
    fun getThumbnail(@Path("wallpaper_name") wallpaper_name: String): Call<ResponseBody>

    @GET("/wallpaper/{wallpaper_name}")
    fun getWallpaperByName(@Path("wallpaper_name") wallpaper_name: String): Call<ResponseBody>

    @GET("/wallpaper/list")
    fun getWallpaperList(): Call<WallpaperListResponse>

    @GET("/tags")
    fun getTags(): Call<TagsResult>


}

data class TagsResult(
    val tags: Array<String>
)

data class WallpaperInfoObject(
    val name: String, val tags: Array<String>
)

data class WallpaperListResponse(
    val wallpapers: Array<WallpaperInfoObject>
)

