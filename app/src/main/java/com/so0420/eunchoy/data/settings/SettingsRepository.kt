package com.so0420.eunchoy.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.so0420.eunchoy.data.Config
import com.so0420.eunchoy.data.SourceKey
import com.so0420.eunchoy.data.naver.NaverSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("eunchoy")

data class SourcePrefs(val notify: Boolean, val alarm: Boolean)

data class AppSettings(
    val sources: Map<SourceKey, SourcePrefs>,
    val xBridgeUrl: String,
    val pollMinutes: Int,
    val fastPolling: Boolean,
    val naverLoggedIn: Boolean,
) {
    fun prefs(key: SourceKey): SourcePrefs = sources[key] ?: SourcePrefs(notify = false, alarm = false)
}

/** DataStore-backed settings + seen-id tracking. One instance per process. */
class SettingsRepository(context: Context) {

    private val ds = context.applicationContext.dataStore

    private fun notifyKey(k: SourceKey) = booleanPreferencesKey("notify_${k.id}")
    private fun alarmKey(k: SourceKey) = booleanPreferencesKey("alarm_${k.id}")
    private fun seenKey(k: SourceKey) = stringPreferencesKey("seen_${k.id}")

    private val xBridge = stringPreferencesKey("x_bridge")
    private val pollMin = intPreferencesKey("poll_minutes")
    private val fastPoll = booleanPreferencesKey("fast_polling")
    private val nidAut = stringPreferencesKey("nid_aut")
    private val nidSes = stringPreferencesKey("nid_ses")

    val settings: Flow<AppSettings> = ds.data.map { p -> read(p) }

    private fun read(p: Preferences): AppSettings {
        val sources = SourceKey.entries.associateWith { k ->
            SourcePrefs(
                notify = p[notifyKey(k)] ?: defaultNotify(k),
                alarm = p[alarmKey(k)] ?: defaultAlarm(k),
            )
        }
        return AppSettings(
            sources = sources,
            xBridgeUrl = p[xBridge] ?: Config.DEFAULT_X_BRIDGE,
            pollMinutes = p[pollMin] ?: 15,
            fastPolling = p[fastPoll] ?: false,
            naverLoggedIn = !p[nidAut].isNullOrBlank() && !p[nidSes].isNullOrBlank(),
        )
    }

    // Sensible defaults: notify on for everything, alarm only for going live.
    private fun defaultNotify(k: SourceKey) = true
    private fun defaultAlarm(k: SourceKey) = k == SourceKey.CHZZK_LIVE

    suspend fun current(): AppSettings = read(ds.data.first())

    suspend fun setNotify(k: SourceKey, value: Boolean) = ds.edit { it[notifyKey(k)] = value }
    suspend fun setAlarm(k: SourceKey, value: Boolean) = ds.edit { it[alarmKey(k)] = value }
    suspend fun setXBridge(url: String) = ds.edit { it[xBridge] = url }
    suspend fun setPollMinutes(min: Int) = ds.edit { it[pollMin] = min }
    suspend fun setFastPolling(value: Boolean) = ds.edit { it[fastPoll] = value }

    suspend fun setNaverCookies(aut: String?, ses: String?) {
        ds.edit {
            if (aut.isNullOrBlank()) it.remove(nidAut) else it[nidAut] = aut
            if (ses.isNullOrBlank()) it.remove(nidSes) else it[nidSes] = ses
        }
        NaverSession.set(aut, ses)
    }

    /** Call once at startup to hydrate [NaverSession] from disk. */
    suspend fun loadNaverIntoSession() {
        val p = ds.data.first()
        NaverSession.set(p[nidAut], p[nidSes])
    }

    suspend fun getSeen(k: SourceKey): String? = ds.data.first()[seenKey(k)]
    suspend fun setSeen(k: SourceKey, value: String) = ds.edit { it[seenKey(k)] = value }
}
