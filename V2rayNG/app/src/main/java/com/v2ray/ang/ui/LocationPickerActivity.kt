package com.v2ray.ang.ui

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityLocationPickerBinding
import com.v2ray.ang.tonic.TonicManager

/**
 * Read-only location picker: the user may only *choose* among the configs that
 * the backend already provisioned. No add / edit / delete / export.
 */
class LocationPickerActivity : BaseActivity() {
    private val binding by lazy { ActivityLocationPickerBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val servers = TonicManager.getServers()
        val rows = buildList {
            // "Auto (fastest)" pseudo-entry first.
            add(
                TonicLocationAdapter.Row(
                    guid = null,
                    name = getString(R.string.tonic_auto_fastest),
                    delayMillis = -1L,
                    selected = servers.none { it.isSelected }
                )
            )
            servers.forEach {
                add(TonicLocationAdapter.Row(it.guid, it.name, it.delayMillis, it.isSelected))
            }
        }

        binding.recyclerLocations.layoutManager = LinearLayoutManager(this)
        binding.recyclerLocations.adapter = TonicLocationAdapter(rows) { guid ->
            if (guid == null) {
                TonicManager.selectFastest()
            } else {
                TonicManager.selectServer(guid)
            }
            setResult(RESULT_OK)
            finish()
        }
    }
}
