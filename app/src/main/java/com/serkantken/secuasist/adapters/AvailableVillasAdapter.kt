package com.serkantken.secuasist.adapters

import android.R
import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.databinding.ItemVillaBinding
import com.serkantken.secuasist.models.SelectableVilla
import com.serkantken.secuasist.utils.Tools
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.createBalloon
import com.skydoves.balloon.overlay.BalloonOverlayCircle
import com.skydoves.balloon.overlay.BalloonOverlayRoundRect
import eightbitlab.com.blurview.BlurView

class AvailableVillasAdapter(
    private val activity: Activity,
    private val onItemClicked: (SelectableVilla) -> Unit
) : RecyclerView.Adapter<AvailableVillasAdapter.VillaViewHolder>() {

    inner class VillaViewHolder(val binding: ItemVillaBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(selectableVilla: SelectableVilla) {
            binding.tvVillaNo.text = selectableVilla.villa.villaNo.toString()
            binding.tvVillaOwnerName.text = selectableVilla.defaultContactName ?: selectableVilla.villa.villaNotes ?: "Bilgi Yok"


            itemView.setOnClickListener {
                if (selectableVilla.villa.isVillaCallForCargo == 0) {
                    onItemClicked(selectableVilla)
                } else {
                    val balloon = createBalloon(itemView.context) {
                        setLayout(com.serkantken.secuasist.R.layout.layout_balloon_no_call_for_cargo)
                        setArrowSize(0)
                        setWidth(BalloonSizeSpec.WRAP)
                        setHeight(BalloonSizeSpec.WRAP)
                        setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.transparent))
                        setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                        setDismissWhenTouchOutside(true)
                        setIsVisibleOverlay(true)
                        setOverlayShape(BalloonOverlayRoundRect(85f, 85f))
                        overlayColor = ContextCompat.getColor(itemView.context, com.serkantken.secuasist.R.color.black_transparent)
                        build()
                    }
                    val blur: BlurView = balloon.getContentView().findViewById<BlurView>(com.serkantken.secuasist.R.id.blur_popup)
                    val buttonAccept: ConstraintLayout = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btnAccept)
                    val buttonDecline: ConstraintLayout = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btnDecline)
                    Tools(activity).blur(arrayOf(blur), 10f, true)
                    balloon.showAlignTop(itemView)

                    buttonAccept.setOnClickListener {
                        onItemClicked(selectableVilla)
                        balloon.dismiss()
                    }
                    buttonDecline.setOnClickListener {
                        balloon.dismiss()
                    }
                }
            }
        }
    }

    private val diffCallback = object : DiffUtil.ItemCallback<SelectableVilla>() {
        override fun areItemsTheSame(oldItem: SelectableVilla, newItem: SelectableVilla): Boolean {
            return oldItem.villa.villaId == newItem.villa.villaId
        }

        override fun areContentsTheSame(oldItem: SelectableVilla, newItem: SelectableVilla): Boolean {
            return oldItem == newItem // Data class karşılaştırması
        }
    }

    val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VillaViewHolder {
        val binding = ItemVillaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VillaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VillaViewHolder, position: Int) {
        val selectableVilla = differ.currentList[position]
        holder.bind(selectableVilla)
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }
}