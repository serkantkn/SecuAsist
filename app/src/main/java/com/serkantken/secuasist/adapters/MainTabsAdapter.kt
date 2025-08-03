package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.R
import com.serkantken.secuasist.databinding.ItemMainTabBinding
import com.serkantken.secuasist.views.activities.MainActivity

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
        notifyDataSetChanged() // Basitlik için, daha performanslı update'ler de düşünülebilir
    }

    inner class TabViewHolder(private val binding: ItemMainTabBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(tab: MainActivity.MainTab) {
            binding.tvTabTitle.text = tab.title
            binding.ivTabIcon.setImageResource(tab.iconResId)

            if (tab.isSelected) {
                binding.root.setBackgroundResource(R.drawable.background_blur) // Seçili arka plan
                // İsteğe bağlı: Seçili ikon ve metin rengini de değiştirebilirsiniz
                //binding.ivTabIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.white)) // Örneğin
                //binding.tvTabTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
            } else {
                binding.root.background = null // Seçili olmayan için arka planı kaldır
                // İsteğe bağlı: Seçili olmayan ikon ve metin rengini varsayılana döndür
                //binding.ivTabIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.textColorSecondary)) // Örneğin
                //binding.tvTabTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.textColorSecondary))
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