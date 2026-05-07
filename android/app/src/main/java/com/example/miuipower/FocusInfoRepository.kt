package com.example.miuipower

object FocusInfoRepository {
    private const val DEFAULT_MAX_AGE_MS = 20_000L

    @Volatile
    private var latestSeen: FocusInfo? = null

    @Volatile
    private var latestExternal: FocusInfo? = null

    @Synchronized
    fun update(info: FocusInfo, selfPackageName: String?) {
        latestSeen = info
        if (!info.packageName.isNullOrBlank() && info.packageName != selfPackageName) {
            latestExternal = info
        }
    }

    fun readLatestExternal(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): FocusInfo? {
        return latestExternal?.takeIf { !isExpired(it, maxAgeMs) }
    }

    fun readLatestSeen(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): FocusInfo? {
        return latestSeen?.takeIf { !isExpired(it, maxAgeMs) }
    }

    private fun isExpired(info: FocusInfo, maxAgeMs: Long): Boolean {
        return System.currentTimeMillis() - info.timestamp > maxAgeMs
    }
}

