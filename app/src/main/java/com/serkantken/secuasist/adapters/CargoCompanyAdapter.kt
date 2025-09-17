package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.databinding.ItemCargoCompanyBinding
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.DisplayCargoCompany

class CargoCompanyAdapter(
    private val onItemLongClick: (CargoCompany) -> Unit // Uzun tıklama için eklendi
) : ListAdapter<DisplayCargoCompany, CargoCompanyAdapter.CargoCompanyViewHolder>(CargoCompanyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CargoCompanyViewHolder {
        val binding = ItemCargoCompanyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CargoCompanyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CargoCompanyViewHolder, position: Int) {
        val displayCompany = getItem(position)
        // onItemClick ve onItemLongClick'i ViewHolder'a iletiyoruz
        holder.bind(displayCompany, onItemLongClick)
    }

    interface OnCargoCompanyActionListener {
        fun onShowActionsClicked(anchorView: View, company: CargoCompany, hasUncalled: Boolean)
    }

    private var actionListener: OnCargoCompanyActionListener? = null

    fun setOnCargoCompanyActionListener(listener: OnCargoCompanyActionListener) {
        this.actionListener = listener
    }

    inner class CargoCompanyViewHolder(private val binding: ItemCargoCompanyBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            displayCompany: DisplayCargoCompany,
            onItemLongClick: (CargoCompany) -> Unit
        ) {
            binding.tvCargoCompanyName.text = displayCompany.company.companyName

            if (displayCompany.hasUncalledCargos) {
                binding.ivPendingCargoIndicator.visibility = View.VISIBLE
            } else {
                binding.ivPendingCargoIndicator.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                this@CargoCompanyAdapter.actionListener?.onShowActionsClicked(
                    binding.root,
                    displayCompany.company,
                    displayCompany.hasUncalledCargos
                )
            }

            binding.root.setOnLongClickListener {
                onItemLongClick(displayCompany.company)
                true
            }
        }
    }

    class CargoCompanyDiffCallback : DiffUtil.ItemCallback<DisplayCargoCompany>() {
        override fun areItemsTheSame(oldItem: DisplayCargoCompany, newItem: DisplayCargoCompany): Boolean {
            return oldItem.company.companyId == newItem.company.companyId
        }

        override fun areContentsTheSame(oldItem: DisplayCargoCompany, newItem: DisplayCargoCompany): Boolean {
            return oldItem == newItem
        }
    }
}
