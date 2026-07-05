package com.v2ray.ang.tonic

import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.LogUtil

/** A server exposed to the (optional) location picker. */
data class TonicServer(
    val guid: String,
    val name: String,
    val delayMillis: Long, // <= 0 means "not tested"
    val isSelected: Boolean
)

/**
 * Bridges the TONIC backend to the existing v2rayNG engine.
 *
 * Responsibilities:
 *  - persist auth token / username (via [TonicStore])
 *  - translate backend subscription links into v2rayNG [SubscriptionItem]s
 *  - drive the existing config import ([AngConfigManager]) and the existing
 *    periodic auto-update ([SubscriptionUpdater])
 *  - auto-select the fastest server
 *
 * Every method here is blocking (network / disk) and must run off the main thread.
 */
object TonicManager {

    /** Subscription refresh interval in minutes, from the brand config. */
    private val updateIntervalMinutes: Long
        get() = BuildConfig.SUB_UPDATE_HOURS * 60L

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    /**
     * Log in and fully provision the app (store token, import configs, select a
     * server). Returns a [TonicResult] whose Success payload is the number of
     * imported configs.
     */
    fun login(username: String, password: String): TonicResult<Int> {
        return when (val res = TonicApi.login(username.trim(), password)) {
            is TonicResult.Success -> {
                TonicStore.token = res.data.token
                TonicStore.username = username.trim()
                val count = provisionSubscriptions(res.data.subscriptions)
                TonicStore.lastBackendSync = System.currentTimeMillis()
                TonicSync.schedule()
                TonicResult.Success(count)
            }

            is TonicResult.Error -> res
        }
    }

    /**
     * Refresh the subscription list from the backend using the stored token,
     * then re-import configs. Used on app open (if stale) and by the periodic
     * worker. Returns true on success.
     */
    fun syncFromBackend(): Boolean {
        val token = TonicStore.token
        if (token.isNullOrEmpty()) return false

        return when (val res = TonicApi.fetchSubscriptions(token)) {
            is TonicResult.Success -> {
                provisionSubscriptions(res.data.subscriptions)
                TonicStore.lastBackendSync = System.currentTimeMillis()
                true
            }

            is TonicResult.Error -> {
                LogUtil.w(AppConfig.TAG, "TonicManager.syncFromBackend failed: ${res.code} ${res.message}")
                false
            }
        }
    }

    /** True if the backend list hasn't been refreshed within the configured interval. */
    fun isBackendSyncStale(): Boolean {
        val last = TonicStore.lastBackendSync
        if (last <= 0L) return true
        return System.currentTimeMillis() - last >= updateIntervalMinutes * 60_000L
    }

    /**
     * Clear all TONIC + engine state. The caller is responsible for stopping the
     * VPN service first.
     */
    fun logout() {
        TonicSync.cancel()
        // Cancel scheduled subscription updates and remove all subs + servers.
        MmkvManager.decodeSubscriptions().forEach { sub ->
            SubscriptionUpdater.cancelOne(subId = sub.guid)
            MmkvManager.removeSubscription(sub.guid)
        }
        MmkvManager.removeAllServer()
        TonicStore.clear()
    }

    // -------------------------------------------------------------------------
    // Subscription provisioning
    // -------------------------------------------------------------------------

    /**
     * Reconcile the backend's subscription list with local storage, then fetch
     * their configs and (re)schedule periodic updates.
     *
     * @return total number of configs available after the update.
     */
    private fun provisionSubscriptions(backendSubs: List<TonicSub>): Int {
        val valid = backendSubs.filter { it.url.isNotBlank() }
        val backendUrls = valid.map { it.url }.toSet()

        val existing = MmkvManager.decodeSubscriptions()
        val existingByUrl = existing.associateBy { it.subscription.url }

        // Remove local subscriptions the backend no longer lists.
        existing.forEach { sub ->
            if (sub.subscription.url !in backendUrls) {
                SubscriptionUpdater.cancelOne(subId = sub.guid)
                MmkvManager.removeSubscription(sub.guid)
            }
        }

        // Add / refresh backend subscriptions.
        valid.forEach { s ->
            val current = existingByUrl[s.url]
            if (current == null) {
                MmkvManager.encodeSubscription("", newSubscriptionItem(s))
            } else {
                // Keep timing/state, just refresh remarks + enforce our policy.
                val item = current.subscription.apply {
                    remarks = s.name.ifBlank { remarks }
                    enabled = true
                    autoUpdate = true
                    updateInterval = updateIntervalMinutes
                    allowInsecureUrl = true
                }
                MmkvManager.encodeSubscription(current.guid, item)
            }
        }

        // Pull the actual node configs for every subscription (existing engine logic).
        val result = AngConfigManager.updateConfigViaSubAll()
        LogUtil.i(
            AppConfig.TAG,
            "TonicManager: provisioned subs=${valid.size} configs=${result.configCount}"
        )

        // (Re)schedule the built-in 12h periodic updater for each subscription.
        SubscriptionUpdater.sync(forceReschedule = true)

        ensureSelectedServer()
        return MmkvManager.decodeAllServerList().size
    }

    private fun newSubscriptionItem(s: TonicSub) = SubscriptionItem(
        remarks = s.name.ifBlank { "TONIC" },
        url = s.url,
        enabled = true,
        autoUpdate = true,
        updateInterval = updateIntervalMinutes,
        allowInsecureUrl = true
    )

    // -------------------------------------------------------------------------
    // Server selection
    // -------------------------------------------------------------------------

    /**
     * Ensure a server is selected. If none is selected (or the selection is
     * stale), pick the fastest tested server, falling back to the first one.
     */
    fun ensureSelectedServer(): String? {
        val all = MmkvManager.decodeAllServerList()
        if (all.isEmpty()) return null

        val current = MmkvManager.getSelectServer()
        if (!current.isNullOrEmpty() && all.contains(current)) return current

        val best = pickFastestGuid(all) ?: all.first()
        MmkvManager.setSelectServer(best)
        return best
    }

    /** Select the fastest tested server explicitly (used by "auto" in the picker). */
    fun selectFastest(): String? {
        val all = MmkvManager.decodeAllServerList()
        if (all.isEmpty()) return null
        val best = pickFastestGuid(all) ?: all.first()
        MmkvManager.setSelectServer(best)
        return best
    }

    private fun pickFastestGuid(guids: List<String>): String? {
        return guids
            .mapNotNull { guid ->
                val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: -1L
                if (delay > 0L) guid to delay else null
            }
            .minByOrNull { it.second }
            ?.first
    }

    /** All available servers, for the optional location picker. */
    fun getServers(): List<TonicServer> {
        val selected = MmkvManager.getSelectServer()
        return MmkvManager.decodeAllServerList().mapNotNull { guid ->
            val profile: ProfileItem = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: -1L
            TonicServer(
                guid = guid,
                name = profile.remarks.ifBlank { profile.server.orEmpty() },
                delayMillis = delay,
                isSelected = guid == selected
            )
        }
    }

    fun selectServer(guid: String) {
        MmkvManager.setSelectServer(guid)
    }
}
