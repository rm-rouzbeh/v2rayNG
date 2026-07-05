package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemTonicLocationBinding

/**
 * Simple list of selectable locations for the TONIC private client.
 * A [guid] of null represents the "Auto (fastest)" entry.
 */
class TonicLocationAdapter(
    private val rows: List<Row>,
    private val onClick: (guid: String?) -> Unit
) : RecyclerView.Adapter<TonicLocationAdapter.VH>() {

    data class Row(
        val guid: String?,
        val name: String,
        val delayMillis: Long,
        val selected: Boolean
    )

    inner class VH(val binding: ItemTonicLocationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTonicLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        holder.binding.tvName.text = row.name
        holder.binding.tvPing.text =
            if (row.delayMillis > 0L) "${row.delayMillis} ms" else ""
        holder.binding.ivCheck.visibility = if (row.selected) View.VISIBLE else View.INVISIBLE
        holder.binding.root.setOnClickListener { onClick(row.guid) }
    }
}
