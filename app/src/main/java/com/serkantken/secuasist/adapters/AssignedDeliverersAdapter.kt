package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.databinding.ItemContactDraggableBinding
import com.serkantken.secuasist.models.Contact

class AssignedDeliverersAdapter(
    private val onRemoveClick: (Contact) -> Unit
) : ListAdapter<Contact, AssignedDeliverersAdapter.ViewHolder>(DelivererDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactDraggableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemContactDraggableBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: Contact) {
            binding.ivDragHandle.visibility = View.INVISIBLE
            binding.tvContactPhone.text = contact.contactPhone
            binding.tvContactName.text = contact.contactName
            binding.ivDeleteContact.setOnClickListener {
                onRemoveClick(contact)
            }
        }
    }

    class DelivererDiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem.contactId == newItem.contactId
        }

        override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem == newItem
        }
    }
}