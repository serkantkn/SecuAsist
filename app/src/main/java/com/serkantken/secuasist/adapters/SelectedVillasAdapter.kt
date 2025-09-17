package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.databinding.ItemSelectedVillaBinding // Oluşturduğunuz item_selected_villa.xml için ViewBinding
import com.serkantken.secuasist.models.SelectableVilla // Seçilen villalar da bu tipi kullanacak

class SelectedVillasAdapter(
    private val onItemClicked: (SelectableVilla) -> Unit
) : RecyclerView.Adapter<SelectedVillasAdapter.SelectedVillaViewHolder>() {

    inner class SelectedVillaViewHolder(val binding: ItemSelectedVillaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(selectableVilla: SelectableVilla) {
            binding.tvSelectedVillaNo.text = selectableVilla.villa.villaNo.toString()

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
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedVillaViewHolder {
        val binding = ItemSelectedVillaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SelectedVillaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SelectedVillaViewHolder, position: Int) {
        val selectableVilla = differ.currentList[position]
        holder.bind(selectableVilla)
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }
}