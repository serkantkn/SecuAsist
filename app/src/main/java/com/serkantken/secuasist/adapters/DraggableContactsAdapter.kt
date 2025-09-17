package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.makeramen.roundedimageview.RoundedImageView
import com.serkantken.secuasist.R
import com.serkantken.secuasist.models.Contact
import java.util.*

class DraggableContactsAdapter(private val onDeleteClick: (Contact) -> Unit) : ListAdapter<Contact, DraggableContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    // Bu fonksiyon, listenin güncel halini dışarıya vermek için kullanılacak.
    fun getFinalList(): List<Contact> {
        return currentList
    }

    // Bu fonksiyon, ItemTouchHelper tarafından çağrılacak ve elemanların yerini değiştirecek.
    fun onRowMoved(fromPosition: Int, toPosition: Int) {
        val mutableList = currentList.toMutableList()
        Collections.swap(mutableList, fromPosition, toPosition)
        // Listeyi doğrudan güncelleyemeyeceğimiz için yeni bir liste olarak submit ediyoruz.
        // Bu, ListAdapter'ın doğru çalışmasını sağlar.
        submitList(mutableList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_draggable, parent, false) // YENİ LAYOUT KULLANILIYOR
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.tvContactName)
        private val phoneTextView: TextView = itemView.findViewById(R.id.tvContactPhone)
        private val deleteButton: RoundedImageView = itemView.findViewById(R.id.ivDeleteContact)

        // Drag handle view'ına da referans alabiliriz, şimdilik gerekmiyor.

        fun bind(contact: Contact) {
            nameTextView.text = contact.contactName
            phoneTextView.text = contact.contactPhone
            deleteButton.setOnClickListener { onDeleteClick(contact) }
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