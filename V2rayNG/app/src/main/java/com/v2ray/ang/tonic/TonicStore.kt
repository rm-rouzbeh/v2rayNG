package com.v2ray.ang.tonic

import com.tencent.mmkv.MMKV

/**
 * Small persistent store for the TONIC private client (auth token, username and
 * the last time we synced the subscription list from the backend).
 *
 * Uses a dedicated MMKV instance in MULTI_PROCESS_MODE because the auth token is
 * also read from the background worker process (":bg") and the VPN daemon
 * process (":RunSoLibV2RayDaemon").
 */
object TonicStore {
    private const val ID = "tonic_store"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"
    private const val KEY_LAST_BACKEND_SYNC = "last_backend_sync"

    private val mmkv by lazy { MMKV.mmkvWithID(ID, MMKV.MULTI_PROCESS_MODE) }

    var token: String?
        get() = mmkv.decodeString(KEY_TOKEN)
        set(value) {
            if (value.isNullOrEmpty()) mmkv.removeValueForKey(KEY_TOKEN) else mmkv.encode(KEY_TOKEN, value)
        }

    var username: String?
        get() = mmkv.decodeString(KEY_USERNAME)
        set(value) {
            if (value.isNullOrEmpty()) mmkv.removeValueForKey(KEY_USERNAME) else mmkv.encode(KEY_USERNAME, value)
        }

    var lastBackendSync: Long
        get() = mmkv.decodeLong(KEY_LAST_BACKEND_SYNC, 0L)
        set(value) {
            mmkv.encode(KEY_LAST_BACKEND_SYNC, value)
        }

    fun isLoggedIn(): Boolean = !token.isNullOrEmpty()

    fun clear() {
        mmkv.clearAll()
    }
}
