package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.R
import com.serkantken.secuasist.models.Contact

class CallingContactAdapter(private val onItemClick: (Contact) -> Unit) : RecyclerView.Adapter<CallingContactAdapter.ContactViewHolder>() {

    var selectedPosition = 0 // Başlangıçta ilk kişi seçili olsun

    private val differCallback = object : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem.contactId == newItem.contactId
        }
        override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem == newItem
        }
    }
    val differ = AsyncListDiffer(this, differCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calling_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(differ.currentList[position])
    }

    override fun getItemCount() = differ.currentList.size

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tv_contact_name)
        private val letters: TextView = itemView.findViewById(R.id.tv_contact_letter)
        private val phone: TextView = itemView.findViewById(R.id.tv_contact_phone)
        private val card: ConstraintLayout = itemView.findViewById(R.id.layout_contact)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    // Önceki seçimi sıfırla
                    notifyItemChanged(selectedPosition)
                    // Yeni pozisyonu ayarla
                    selectedPosition = adapterPosition
                    // Yeni seçimi vurgula
                    notifyItemChanged(selectedPosition)
                    // Tıklama olayını CallingActivity'e bildir
                    onItemClick(differ.currentList[adapterPosition])
                }
            }
        }

        fun bind(contact: Contact) {
            name.text = contact.contactName
            letters.text = contact.contactName?.take(1) ?: ""
            phone.text = contact.contactPhone

            // Seçili olanı vurgula
            if (adapterPosition == selectedPosition) {
                card.background = ContextCompat.getDrawable(itemView.context, R.drawable.background_edittext)
            } else {
                card.background = null
            }
        }
    }
}