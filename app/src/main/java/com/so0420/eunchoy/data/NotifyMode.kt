package com.so0420.eunchoy.data

/** How a source notifies. [id] is a stable string persisted in prefs (R8-safe). */
enum class NotifyMode(val id: String) {
    OFF("off"),       // 알림 안 함
    NORMAL("normal"), // 일반 푸시 알림 (heads-up)
    ALARM("alarm");   // 알람처럼 계속 울림 (DND/무음 통과)

    companion object {
        fun from(id: String?): NotifyMode? = entries.firstOrNull { it.id == id }
    }
}
