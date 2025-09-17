package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.databinding.ItemStreetFilterBinding

class StreetFilterAdapter(
    private val streetList: List<String>,
    private val onStreetClicked: (String) -> Unit
) : RecyclerView.Adapter<StreetFilterAdapter.StreetViewHolder>() {

    inner class StreetViewHolder(private val binding: ItemStreetFilterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(streetName: String) {
            binding.tvStreetName.text = streetName
            itemView.setOnClickListener {
                onStreetClicked(streetName)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreetViewHolder {
        val binding = ItemStreetFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StreetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StreetViewHolder, position: Int) {
        holder.bind(streetList[position])
    }

    override fun getItemCount(): Int {
        return streetList.size
    }
}