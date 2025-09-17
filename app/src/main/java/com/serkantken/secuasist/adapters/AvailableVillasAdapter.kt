package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.databinding.ItemVillaBinding
import com.serkantken.secuasist.models.SelectableVilla

class AvailableVillasAdapter(
    private val onItemClicked: (SelectableVilla) -> Unit
) : RecyclerView.Adapter<AvailableVillasAdapter.VillaViewHolder>() {

    inner class VillaViewHolder(val binding: ItemVillaBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(selectableVilla: SelectableVilla) {
            binding.tvVillaNo.text = selectableVilla.villa.villaNo.toString()
            binding.tvVillaOwnerName.text = selectableVilla.defaultContactName ?: selectableVilla.villa.villaNotes ?: "Bilgi Yok"


            itemView.setOnClickListener {
                onItemClicked(selectableVilla)
            }
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<SelectableVilla>() {
        override fun areItemsTheSame(oldItem: SelectableVilla, newItem: SelectableVilla): Boolean {
            return oldItem.villa.villaId == newItem.villa.villaId
        }

        override fun areContentsTheSame(oldItem: SelectableVilla, newItem: SelectableVilla): Boolean {
            return oldItem == newItem // Data class karşılaştırması
        }
    }

    val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VillaViewHolder {
        val binding = ItemVillaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VillaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VillaViewHolder, position: Int) {
        val selectableVilla = differ.currentList[position]
        holder.bind(selectableVilla)
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }
}