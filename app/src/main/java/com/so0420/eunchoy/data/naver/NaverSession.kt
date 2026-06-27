package com.so0420.eunchoy.data.naver

/**
 * Holds the current Naver session cookies (NID_AUT / NID_SES) in memory.
 * Loaded from DataStore at startup and updated after an in-app WebView login.
 * The OkHttp interceptor reads [cookieHeader] to authenticate cafe / adult-stream calls.
 */
object NaverSession {
    @Volatile
    var nidAut: String? = null
        private set

    @Volatile
    var nidSes: String? = null
        private set

    val isLoggedIn: Boolean
        get() = !nidAut.isNullOrBlank() && !nidSes.isNullOrBlank()

    fun cookieHeader(): String? =
        if (isLoggedIn) "NID_AUT=$nidAut; NID_SES=$nidSes" else null

    fun set(aut: String?, ses: String?) {
        nidAut = aut?.takeIf { it.isNotBlank() }
        nidSes = ses?.takeIf { it.isNotBlank() }
    }

    fun clear() {
        nidAut = null
        nidSes = null
    }
}
