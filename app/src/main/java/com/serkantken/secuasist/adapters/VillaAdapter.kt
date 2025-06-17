package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.databinding.ItemVillaBinding
import com.serkantken.secuasist.models.Villa

class VillaAdapter(
    private val onItemClick: (Villa) -> Unit, // Tıklama listener'ı
    private val onItemLongClick: (Villa) -> Boolean // Uzun basma listener'ı
) : ListAdapter<Villa, VillaAdapter.VillaViewHolder>(VillaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VillaViewHolder {
        val binding = ItemVillaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VillaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VillaViewHolder, position: Int) {
        val villa = getItem(position)
        holder.bind(villa)
    }

    inner class VillaViewHolder(private val binding: ItemVillaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Tıklama olayını ayarla
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            // Uzun basma olayını ayarla
            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClick(getItem(position))
                } else {
                    false // Tıklama işlenmedi
                }
            }
        }

        fun bind(villa: Villa) {
            binding.tvVillaNo.text = villa.villaNo.toString()

            // Villa sahibini veya birden fazla kişiyi çekmek için Room'dan ayrı bir sorgu gerekecek.
            // Şimdilik sadece "Sahibi Bilinmiyor" gibi bir placeholder kullanalım.
            // Bu kısım daha sonra ViewModel'e benzer bir yapı kurarsak gelişebilir.
            // Veya sadece ilk kişiyi gösterebiliriz.
            binding.tvVillaOwnerName.text = "Sahibi: Bilinmiyor" // Placeholder

            // TODO: Daha sonra buraya asıl villa sahibi/kiracı bilgisini getirme mantığı eklenecek
            // Bu adaptör, doğrudan DAO'lara erişmemeli. Activity/Fragment bu bilgiyi sağlayacak.
        }
    }

    // Listeyi verimli bir şekilde güncellemek için DiffUtil Callback
    class VillaDiffCallback : DiffUtil.ItemCallback<Villa>() {
        override fun areItemsTheSame(oldItem: Villa, newItem: Villa): Boolean {
            return oldItem.villaId == newItem.villaId
        }

        override fun areContentsTheSame(oldItem: Villa, newItem: Villa): Boolean {
            return oldItem == newItem
        }
    }
}