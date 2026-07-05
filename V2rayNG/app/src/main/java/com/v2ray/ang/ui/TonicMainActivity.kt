package com.v2ray.ang.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.net.VpnService
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.databinding.ActivityTonicMainBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.tonic.TonicManager
import com.v2ray.ang.tonic.TonicStore
import com.v2ray.ang.tonic.TonicSync
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TONIC main screen: one big connect button, live status and an optional
 * location picker. All connection plumbing reuses the existing v2rayNG engine
 * ([CoreServiceManager] + service broadcasts).
 */
class TonicMainActivity : BaseActivity() {
    private val binding by lazy { ActivityTonicMainBinding.inflate(layoutInflater) }

    private enum class UiState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING }

    private var state = UiState.DISCONNECTED

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startCore()
            } else {
                setState(UiState.DISCONNECTED)
            }
        }

    private val requestLocationPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateLocationLabel()
            if (it.resultCode == RESULT_OK && state == UiState.CONNECTED) {
                restartCore()
            }
        }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING,
                AppConfig.MSG_STATE_START_SUCCESS -> setState(UiState.CONNECTED)

                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_STOP_SUCCESS -> setState(UiState.DISCONNECTED)

                AppConfig.MSG_STATE_START_FAILURE -> {
                    toastError(R.string.toast_services_failure)
                    setState(UiState.DISCONNECTED)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Guard: never show the main screen without a session.
        if (!TonicStore.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(binding.root)

        binding.btnConnect.setOnClickListener { onConnectClicked() }
        binding.btnRefresh.setOnClickListener { refresh() }
        binding.btnLogout.setOnClickListener { confirmLogout() }

        if (BuildConfig.ALLOW_LOCATION_PICKER) {
            binding.layoutLocation.visibility = View.VISIBLE
            binding.layoutLocation.setOnClickListener {
                requestLocationPicker.launch(Intent(this, LocationPickerActivity::class.java))
            }
        } else {
            binding.layoutLocation.visibility = View.GONE
        }

        ContextCompat.registerReceiver(
            this, stateReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags()
        )

        setState(UiState.DISCONNECTED)
        updateLocationLabel()

        // Keep the built-in periodic subscription updater scheduled, plus our
        // backend-list refresh worker (both run every BRAND_SUB_UPDATE_HOURS).
        SubscriptionUpdater.sync()
        TonicSync.schedule()
        maybeSyncOnOpen()
    }

    override fun onResume() {
        super.onResume()
        // Ask the service for the current running state.
        MessageUtil.sendMsg2Service(this, AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stateReceiver) }
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Connection
    // -------------------------------------------------------------------------

    private fun onConnectClicked() {
        when (state) {
            UiState.CONNECTED -> {
                setState(UiState.DISCONNECTING)
                CoreServiceManager.stopVService(this)
            }

            UiState.DISCONNECTED -> {
                if (MmkvManager.decodeAllServerList().isEmpty()) {
                    toast(R.string.tonic_no_servers)
                    refresh()
                    return
                }
                setState(UiState.CONNECTING)
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startCore()
                } else {
                    requestVpnPermission.launch(intent)
                }
            }

            else -> {
                // Ignore taps while a transition is in progress.
            }
        }
    }

    private fun startCore() {
        val selected = TonicManager.ensureSelectedServer()
        if (selected.isNullOrEmpty()) {
            toast(R.string.tonic_no_servers)
            setState(UiState.DISCONNECTED)
            return
        }
        CoreServiceManager.startVService(this)
    }

    private fun restartCore() {
        setState(UiState.CONNECTING)
        CoreServiceManager.stopVService(this)
        lifecycleScope.launch {
            delay(800)
            startCore()
        }
    }

    // -------------------------------------------------------------------------
    // Backend refresh
    // -------------------------------------------------------------------------

    private fun refresh() {
        toast(R.string.tonic_updating_servers)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { TonicManager.syncFromBackend() }
            updateLocationLabel()
            if (ok) toast(R.string.tonic_servers_updated) else toastError(R.string.tonic_network_error)
        }
    }

    private fun maybeSyncOnOpen() {
        if (!TonicManager.isBackendSyncStale()) return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { TonicManager.syncFromBackend() }
            updateLocationLabel()
        }
    }

    // -------------------------------------------------------------------------
    // UI state
    // -------------------------------------------------------------------------

    private fun setState(newState: UiState) {
        state = newState
        val (circleColor, status, subStatus) = when (newState) {
            UiState.DISCONNECTED -> Triple(R.color.brand_disconnected, R.string.tonic_disconnected, R.string.tonic_status_disconnected)
            UiState.CONNECTING -> Triple(R.color.brand_primary, R.string.tonic_connecting, R.string.tonic_status_disconnected)
            UiState.CONNECTED -> Triple(R.color.brand_connected, R.string.tonic_connected, R.string.tonic_status_connected)
            UiState.DISCONNECTING -> Triple(R.color.brand_primary, R.string.tonic_disconnecting, R.string.tonic_status_connected)
        }
        binding.btnConnect.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, circleColor))
        binding.tvStatus.setText(status)
        binding.tvSubStatus.setText(subStatus)
    }

    private fun updateLocationLabel() {
        if (!BuildConfig.ALLOW_LOCATION_PICKER) return
        val servers = TonicManager.getServers()
        val selected = servers.firstOrNull { it.isSelected }
        binding.tvLocation.text = selected?.name ?: getString(R.string.tonic_auto_fastest)
        val delay = selected?.delayMillis ?: -1L
        binding.tvPing.text = if (delay > 0L) getString(R.string.tonic_ms, delay.toInt()) else ""
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setMessage(R.string.tonic_logout)
            .setPositiveButton(android.R.string.ok) { _, _ -> doLogout() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doLogout() {
        if (state == UiState.CONNECTED || state == UiState.CONNECTING) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { TonicManager.logout() }
            startActivity(Intent(this@TonicMainActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }
}
