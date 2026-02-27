package com.hunttv.streamtv.network

import com.hunttv.streamtv.models.StreamResponse
import com.hunttv.streamtv.models.BannerConfig
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * API interface for fetching streams and banner
 */
interface StreamAPI {
    
    @GET("api/streams.json")
    suspend fun getStreams(
        @Header("X-API-Key") apiKey: String,
        @Header("User-Agent") userAgent: String
    ): StreamResponse
    
    @GET("api/banner")
    suspend fun getBannerConfig(
        @Header("X-API-Key") apiKey: String
    ): BannerConfig
}
