package com.so0420.eunchoy.data.net

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** api.chzzk.naver.com */
interface ChzzkApi {
    @GET("service/v3/channels/{channelId}/live-detail")
    suspend fun liveDetail(@Path("channelId") channelId: String): Envelope<LiveDetail>

    @GET("service/v1/channels/{channelId}")
    suspend fun channel(@Path("channelId") channelId: String): Envelope<ChannelProfile>

    @GET("service/v1/channels/{channelId}/videos")
    suspend fun videos(
        @Path("channelId") channelId: String,
        @Query("sortType") sortType: String = "LATEST",
        @Query("pagingType") pagingType: String = "PAGE",
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): Envelope<VideoListContent>

    @GET("service/v2/videos/{videoNo}")
    suspend fun videoDetail(@Path("videoNo") videoNo: Long): Envelope<VideoDetail>
}

/** apis.naver.com — Chzzk community comments + Naver Cafe. */
interface NaverApi {
    @GET("nng_main/nng_comment_api/v1/type/CHANNEL_POST/id/{channelId}/comments")
    suspend fun communityComments(
        @Path("channelId") channelId: String,
        @Query("limit") limit: Int = 10,            // MUST be 10 or 30
        @Query("offset") offset: Int = 0,
        @Query("orderType") orderType: String = "DESC",
        @Query("pagingType") pagingType: String = "PAGE",
    ): Envelope<CommentContent>

    // Public cafe-wide list (no login). Filter rows by memberKey client-side.
    @GET("cafe-web/cafe2/ArticleListV2dot1.json")
    suspend fun cafeArticleList(
        @Query("search.clubid") clubId: Long,
        @Query("search.queryType") queryType: String = "lastArticle",
        @Query("search.page") page: Int = 1,
        @Query("search.perPage") perPage: Int = 40,
    ): CafeListResponse

    // Single article content (open posts need no login; member-only needs cookies).
    @GET("cafe-web/cafe-articleapi/v3/cafes/{cafeId}/articles/{articleId}")
    suspend fun cafeArticle(
        @Path("cafeId") cafeId: Long,
        @Path("articleId") articleId: Long,
        @Query("query") query: String = "",
        @Query("useCafeId") useCafeId: Boolean = true,
        @Query("requestFrom") requestFrom: String = "A",
    ): CafeArticleResponse

    // All comments for an article (the base endpoint only returns the first page).
    @GET("cafe-web/cafe-articleapi/v3/cafes/{cafeId}/articles/{articleId}/comments")
    suspend fun cafeArticleComments(
        @Path("cafeId") cafeId: Long,
        @Path("articleId") articleId: Long,
        @Query("query") query: String = "",
        @Query("requestFrom") requestFrom: String = "A",
    ): CafeArticleResponse
}
