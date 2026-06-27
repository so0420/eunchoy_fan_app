package com.so0420.eunchoy.data.net

import kotlinx.serialization.Serializable

// ---- Public cafe article list (cafe2/ArticleListV2dot1.json) — no login ----

@Serializable
data class CafeListResponse(val message: CafeMessage = CafeMessage())

@Serializable
data class CafeMessage(
    val status: String = "",
    val error: CafeError? = null,
    val result: CafeListResult? = null,
)

@Serializable
data class CafeError(val code: String = "", val msg: String = "")

@Serializable
data class CafeListResult(
    val cafeName: String? = null,
    val hasNext: Boolean = false,
    val articleList: List<CafeArticleRow> = emptyList(),
)

@Serializable
data class CafeArticleRow(
    val articleId: Long = 0,
    val subject: String = "",
    val writeDateTimestamp: Long = 0,      // epoch millis
    val writerNickname: String = "",
    val memberKey: String = "",
    val readCount: Int = 0,
    val commentCount: Int = 0,
    val likeItCount: Int = 0,
    val openArticle: Boolean = true,
    val attachImage: Boolean = false,
    val menuId: Int = 0,
    val menuName: String? = null,
)

// ---- Single article content (cafe-articleapi/v3) ----

@Serializable
data class CafeArticleResponse(val result: CafeArticleResult = CafeArticleResult())

@Serializable
data class CafeArticleResult(
    val errorCode: String? = null,         // non-null => error (0004 login, 4003 missing)
    val reason: String? = null,
    val article: CafeArticle? = null,
    val user: CafeSessionUser? = null,
)

@Serializable
data class CafeArticle(
    val id: Long = 0,
    val subject: String = "",
    val contentHtml: String = "",
    val writeDate: Long = 0,               // epoch millis
    val readCount: Int = 0,
    val commentCount: Int = 0,
    val isOpen: Boolean = true,
    val isReadable: Boolean = true,
    val isBlind: Boolean = false,
    val writer: CafeWriter? = null,
    val menu: CafeMenu? = null,
)

@Serializable
data class CafeWriter(
    val memberKey: String = "",
    val nick: String = "",
    val image: CafeImageRef? = null,
    val memberLevelName: String? = null,
)

@Serializable
data class CafeImageRef(val url: String = "")

@Serializable
data class CafeMenu(val id: Long = 0, val name: String = "")

@Serializable
data class CafeSessionUser(
    val isLogin: Boolean = false,
    val isCafeMember: Boolean = false,
)
