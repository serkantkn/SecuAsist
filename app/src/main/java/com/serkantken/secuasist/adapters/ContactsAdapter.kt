package com.serkantken.secuasist.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.databinding.ItemContactBinding
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.VillaContact
import androidx.core.view.isGone
import androidx.core.view.setPadding
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.github.marlonlom.utilities.timeago.TimeAgo
import com.github.marlonlom.utilities.timeago.TimeAgoMessages
import com.serkantken.secuasist.utils.Tools

class ContactsAdapter(
    val activity: Activity,
    private val onItemClick: (Contact, VillaContact?) -> Unit,
    private val onCallClick: (Contact) -> Unit,
    private val onDeleteClick: (Contact) -> Unit,
    val isShowingInfo: Boolean,
    val isChoosingContact: Boolean
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
            binding.tvContactLetter.text = contact.contactName?.take(1) ?: ""
            binding.tvContactPhone.text = contact.contactPhone
            if (contact.lastCallTimestamp != null && contact.lastCallTimestamp!! > 0L) {
                binding.tvRecentCallDate.text = TimeAgo.using(contact.lastCallTimestamp!!)
            } else {
                binding.tvRecentCallDate.text = "Hiç aranmadı"
            }
            if (isShowingInfo) {
                binding.btnEdit.visibility = View.GONE
                binding.btnDelete.visibility = View.GONE
            } else {
                binding.btnEdit.visibility = View.VISIBLE
                binding.btnDelete.visibility = View.VISIBLE
            }
            if (isChoosingContact) {
                binding.ivCallContact.visibility = View.GONE
                binding.root.setOnClickListener { onItemClick(contact, null) }
            } else {
                // Tıklama listener'ı (düzenleme için)
                binding.root.setOnClickListener {
                    if (binding.areaDetail.isGone) {
                        binding.areaDetail.alpha = 0f
                        TransitionManager.beginDelayedTransition(binding.root as ViewGroup,
                            ChangeBounds().setDuration(300))
                        binding.areaDetail.visibility = View.VISIBLE
                        binding.areaDetail.animate().alpha(1f).setDuration(200).start()
                        binding.ivCallContact.animate().alpha(0f).setDuration(200).withEndAction {
                            binding.ivCallContact.visibility = View.GONE
                        }.start()
                        binding.root.background = AppCompatResources.getDrawable(binding.root.context, com.serkantken.secuasist.R.drawable.background_edittext)
                    } else {
                        binding.areaDetail.animate().alpha(0f).setDuration(200).withEndAction {
                            TransitionManager.beginDelayedTransition(binding.root as ViewGroup,
                                ChangeBounds().setDuration(300))
                            binding.areaDetail.visibility = View.GONE
                            binding.ivCallContact.alpha = 0f
                            binding.ivCallContact.visibility = View.VISIBLE
                            binding.ivCallContact.animate().alpha(1f).setDuration(300).withEndAction {
                                TransitionManager.beginDelayedTransition(binding.root as ViewGroup,
                                    ChangeBounds().setDuration(200))
                                binding.root.background = AppCompatResources.getDrawable(binding.root.context, com.serkantken.secuasist.R.drawable.background_blur)
                            }.start()
                        }.start()
                    }
                }

                binding.btnEdit.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onItemClick(getItem(position), null) // Şimdilik VillaContact null
                    }
                }

                binding.btnDelete.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onDeleteClick(getItem(position))
                    }
                }

                binding.ivCallContact.setOnClickListener { onCallClick(contact) }
                binding.btnCallDetail.setOnClickListener { onCallClick(contact) }
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