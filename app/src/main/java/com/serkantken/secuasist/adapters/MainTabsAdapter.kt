package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.R
import com.serkantken.secuasist.databinding.ItemMainTabBinding
import com.serkantken.secuasist.views.activities.MainActivity

@ExperimentalBadgeUtils
class MainTabsAdapter(
    private val onItemClicked: (MainActivity.MainTab) -> Unit
) : ListAdapter<MainActivity.MainTab, MainTabsAdapter.TabViewHolder>(TabDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemMainTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
        notifyDataSetChanged()
    }

    inner class TabViewHolder(private val binding: ItemMainTabBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(tab: MainActivity.MainTab) {
            binding.tvTabTitle.text = tab.title
            binding.ivTabIcon.setImageResource(tab.iconResId)

            if (tab.id == 2 && tab.hasNotification) binding.ivPendingCargoIndicator.visibility = View.VISIBLE else binding.ivPendingCargoIndicator.visibility = View.GONE

            if (tab.isSelected) {
                if (Hawk.contains("less_blur")) {
                    if (Hawk.get<Boolean>("less_blur") == true) {
                        binding.root.setBackgroundResource(R.drawable.background_no_blur)
                    } else {
                        binding.root.setBackgroundResource(R.drawable.background_blur)
                    }
                } else {
                    binding.root.setBackgroundResource(R.drawable.background_blur)
                }
                binding.ivTabIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.colorSecondary))
                binding.tvTabTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.colorSecondary))
            } else {
                binding.root.background = null
                binding.ivTabIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.white))
                binding.tvTabTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
            }

            binding.root.setOnClickListener {
                onItemClicked(tab)
            }
        }
    }

    class TabDiffCallback : DiffUtil.ItemCallback<MainActivity.MainTab>() {
        override fun areItemsTheSame(oldItem: MainActivity.MainTab, newItem: MainActivity.MainTab): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MainActivity.MainTab, newItem: MainActivity.MainTab): Boolean {
            return oldItem == newItem
        }
    }
}