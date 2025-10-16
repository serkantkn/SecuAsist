package com.serkantken.secuasist.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.R
import com.serkantken.secuasist.databinding.ItemVillaBinding
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaWithContacts
import com.serkantken.secuasist.utils.Tools

class VillaAdapter(
    // Artık VillaWithContacts objelerini işleyeceğiz
    private val onItemClick: (Villa) -> Unit, // Tıklama listener'ı (hala Villa objesi dönsün)
    private val onItemLongClick: (Villa) -> Boolean // Uzun basma listener'ı (hala Villa objesi dönsün)
) : ListAdapter<VillaWithContacts, VillaAdapter.VillaViewHolder>(VillaDiffCallback()) { // VILLA -> VILLA_WITH_CONTACTS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VillaViewHolder {
        val binding = ItemVillaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VillaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VillaViewHolder, position: Int) {
        val villaWithContacts = getItem(position)
        holder.bind(villaWithContacts)
    }

    inner class VillaViewHolder(private val binding: ItemVillaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            if (Hawk.contains("enable_blur")) {
                if (Hawk.get<Boolean>("enable_blur") == false) {
                    binding.root.setBackgroundResource(R.drawable.background_no_blur)
                } else {
                    binding.root.setBackgroundResource(R.drawable.background_blur)
                }
            } else {
                binding.root.setBackgroundResource(R.drawable.background_blur)
            }
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position).villa) // Sadece Villa objesini geri döndür
                }
            }
            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClick(getItem(position).villa) // Sadece Villa objesini geri döndür
                } else {
                    false
                }
            }
        }

        fun bind(villaWithContacts: VillaWithContacts) {
            binding.tvVillaNo.text = villaWithContacts.villa.villaNo.toString()

            // Ana iletişim kurulacak kişiyi bul (örneğin ilk kişi veya isRealOwner=0 olan ilk kişi)
            val primaryContact = villaWithContacts.contacts.firstOrNull { it.contactName?.isNotBlank()
                ?: false } // Sadece adı olan ilk kişiyi al
            // Veya daha spesifik: isRealOwner = 0 (kiracı) olan birincil kişi
            // val tenant = villaWithContacts.contacts.firstOrNull { it.contactType == "Tenant" }

            if (Hawk.contains("less_animations") && Hawk.get("less_animations")) {
                if (Hawk.get("less_animations")) {
                    binding.tvVillaOwnerName.isSelected = false
                } else {
                    binding.tvVillaOwnerName.isSelected = true
                }
            } else {
                binding.tvVillaOwnerName.isSelected = true
            }
            if (primaryContact != null) {
                binding.tvVillaOwnerName.text = primaryContact.contactName
            } else {
                binding.tvVillaOwnerName.text = "Sahibi Yok / Bilinmiyor"
            }
        }
    }

    // Listeyi verimli bir şekilde güncellemek için DiffUtil Callback
    // VillaWithContacts objelerini karşılaştıracağız
    class VillaDiffCallback : DiffUtil.ItemCallback<VillaWithContacts>() {
        override fun areItemsTheSame(oldItem: VillaWithContacts, newItem: VillaWithContacts): Boolean {
            return oldItem.villa.villaId == newItem.villa.villaId
        }

        override fun areContentsTheSame(oldItem: VillaWithContacts, newItem: VillaWithContacts): Boolean {
            return oldItem == newItem
        }
    }
}