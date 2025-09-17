package com.serkantken.secuasist.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.green
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.R
import com.serkantken.secuasist.databinding.ItemVillaBinding
import com.serkantken.secuasist.models.DisplayableVilla
import com.serkantken.secuasist.models.VillaCallingState

class CallingVillaAdapter(
    private val onItemClicked: (DisplayableVilla) -> Unit
) : ListAdapter<DisplayableVilla, CallingVillaAdapter.VillaViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VillaViewHolder {
        val binding = ItemVillaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VillaViewHolder(binding, parent.context)
    }

    override fun onBindViewHolder(holder: VillaViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClicked)
    }

    inner class VillaViewHolder(
        private val binding: ItemVillaBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(displayableVilla: DisplayableVilla, onItemClicked: (DisplayableVilla) -> Unit) {
            binding.apply {
                tvVillaNo.text = displayableVilla.villa.villaNo.toString()
                tvVillaOwnerName.text = displayableVilla.ownerName ?: "Sahibi Yok / Bilinmiyor"

                when (displayableVilla.state) {
                    VillaCallingState.IN_PROGRESS -> {
                        cardViewVilla.background = ContextCompat.getDrawable(context, R.drawable.background_blur)
                    }
                    VillaCallingState.PENDING -> {
                        cardViewVilla.background = ContextCompat.getDrawable(context, R.drawable.background_blur_success_villa)
                    }
                    VillaCallingState.CALLED_FAILED -> {
                        cardViewVilla.background = ContextCompat.getDrawable(context, R.drawable.background_blur_pending_villa)
                    }
                    VillaCallingState.CALLED_SUCCESS -> {
                        cardViewVilla.background = ContextCompat.getDrawable(context, R.drawable.background_blur_called_villa)
                    }
                    VillaCallingState.DONE_AT_HOME -> {
                        cardViewVilla.background = ContextCompat.getDrawable(context, R.drawable.background_blur_called_villa)
                    }

                    VillaCallingState.CALLED -> {
                        cardViewVilla.background = ContextCompat.getDrawable(context, R.drawable.background_blur_success_villa)
                    }
                }

                itemView.setOnClickListener {
                    onItemClicked(displayableVilla)
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DisplayableVilla>() {
        override fun areItemsTheSame(oldItem: DisplayableVilla, newItem: DisplayableVilla) =
            oldItem.villa.villaId == newItem.villa.villaId

        override fun areContentsTheSame(oldItem: DisplayableVilla, newItem: DisplayableVilla) =
            oldItem == newItem
    }
}