package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.databinding.ItemContactBinding
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.VillaContact

// onItemClick: Kişi öğesine tıklandığında (düzenleme için)
// onCallClick: Telefon simgesine tıklandığında (arama için) - İleride eklenebilir
// onDeleteClick: Silme simgesine tıklandığında (silme için) - İleride eklenebilir
class ContactsAdapter(
    private val onItemClick: (Contact, VillaContact?) -> Unit, // VillaContact'ı da geçirelim
    private val onDeleteClick: (Contact, VillaContact?) -> Unit // Silme işlevi için
) : ListAdapter<Contact, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = getItem(position)
        holder.bind(contact)
    }

    inner class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.tvContactName.text = contact.contactName
            binding.tvContactPhone.text = contact.contactPhone

            // Tıklama listener'ı (düzenleme için)
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position), null) // Şimdilik VillaContact null
                }
            }
            binding.ivDeleteContact.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // Benzer şekilde, onDeleteClick de (Contact, VillaContact?) bekliyor.
                    onDeleteClick(getItem(position), null) // Şimdilik VillaContact null
                }
            }
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem.contactId == newItem.contactId
        }

        override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem == newItem
        }
    }
}