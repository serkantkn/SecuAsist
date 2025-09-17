package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.R
import com.serkantken.secuasist.databinding.ItemFaultTabBinding
import com.serkantken.secuasist.views.activities.MainActivity
import com.serkantken.secuasist.views.fragments.FaultTrackingFragment

class FaultTabsAdapter(
    private val onItemClicked: (FaultTrackingFragment.FaultTab) -> Unit
) : ListAdapter<FaultTrackingFragment.FaultTab, FaultTabsAdapter.TabViewHolder>(TabDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemFaultTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun selectTab(position: Int) {
        if (position < 0 || position >= itemCount) return

        currentList.forEachIndexed { index, tab ->
            tab.isSelected = index == position
        }
        notifyDataSetChanged() // Basitlik için, daha performanslı update'ler de düşünülebilir
    }

    inner class TabViewHolder(private val binding: ItemFaultTabBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(tab: FaultTrackingFragment.FaultTab) {
            binding.tvTabTitle.text = tab.title

            if (tab.isSelected) {
                binding.root.setBackgroundResource(R.drawable.background_blur)
                binding.tvTabTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorSecondary))
            } else {
                binding.root.background = null
                binding.tvTabTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
            }

            binding.root.setOnClickListener {
                onItemClicked(tab)
            }
        }
    }

    class TabDiffCallback : DiffUtil.ItemCallback<FaultTrackingFragment.FaultTab>() {
        override fun areItemsTheSame(oldItem: FaultTrackingFragment.FaultTab, newItem: FaultTrackingFragment.FaultTab): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FaultTrackingFragment.FaultTab, newItem: FaultTrackingFragment.FaultTab): Boolean {
            return oldItem == newItem
        }
    }
}