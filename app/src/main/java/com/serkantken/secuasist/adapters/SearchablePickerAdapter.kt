package com.serkantken.secuasist.adapters // veya sizin paket adınız

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.serkantken.secuasist.databinding.ItemCargoCompanyBinding
import com.serkantken.secuasist.databinding.ItemSelectedVillaBinding
import com.serkantken.secuasist.models.SearchableItem
import java.util.Locale

class SearchablePickerAdapter(
    private var originalItems: List<SearchableItem>,
    private val itemLayoutType: ItemLayoutType, // Villa mı Şirket mi olduğunu belirtecek enum
    private val onItemClick: (SearchableItem) -> Unit
) : RecyclerView.Adapter<SearchablePickerAdapter.BaseViewHolder<*>>() {

    private var filteredItems: MutableList<SearchableItem> = originalItems.toMutableList()

    // Hangi layout'un kullanılacağını belirlemek için enum
    enum class ItemLayoutType {
        COMPANY, // item_cargo_company.xml için (Linear Layout)
        SELECTED_VILLA // item_selected_villa.xml için (Grid Layout)
    }

    // Farklı ViewHolder'lar için temel sınıf
    abstract class BaseViewHolder<T : ViewBinding>(val binding: T) : RecyclerView.ViewHolder(binding.root) {
        abstract fun bind(item: SearchableItem)
    }

    // item_cargo_company.xml için ViewHolder
    inner class CompanyViewHolder(binding: ItemCargoCompanyBinding) : BaseViewHolder<ItemCargoCompanyBinding>(binding) {
        override fun bind(item: SearchableItem) {
            binding.tvCargoCompanyName.text = item.getDisplayName()
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    // item_selected_villa.xml için ViewHolder
    inner class SelectedVillaViewHolder(binding: ItemSelectedVillaBinding) : BaseViewHolder<ItemSelectedVillaBinding>(binding) {
        override fun bind(item: SearchableItem) {
            binding.tvSelectedVillaNo.text = item.getDisplayName()
            // item_selected_villa.xml'de alt metin yoktu, gerekirse eklenebilir.
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return itemLayoutType.ordinal // Enum'ın sırasını view type olarak kullan
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<*> {
        val inflater = LayoutInflater.from(parent.context)
        return when (ItemLayoutType.entries[viewType]) {
            ItemLayoutType.COMPANY -> {
                val binding = ItemCargoCompanyBinding.inflate(inflater, parent, false)
                CompanyViewHolder(binding)
            }
            ItemLayoutType.SELECTED_VILLA -> {
                val binding = ItemSelectedVillaBinding.inflate(inflater, parent, false)
                SelectedVillaViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<*>, position: Int) {
        holder.bind(filteredItems[position])
    }

    override fun getItemCount(): Int = filteredItems.size

    fun filter(query: String?) {
        filteredItems.clear()
        if (query.isNullOrEmpty()) {
            filteredItems.addAll(originalItems)
        } else {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            originalItems.forEach { item ->
                if (item.getDisplayName()?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true) {
                    filteredItems.add(item)
                }
            }
        }
        notifyDataSetChanged()
    }
}
