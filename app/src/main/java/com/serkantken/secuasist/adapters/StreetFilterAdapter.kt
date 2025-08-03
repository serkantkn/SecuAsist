package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.databinding.ItemStreetFilterBinding

class StreetFilterAdapter(
    private val streetList: List<String>,
    private val onStreetClicked: (String) -> Unit // Bir sokağa tıklandığında çalışacak fonksiyon
) : RecyclerView.Adapter<StreetFilterAdapter.StreetViewHolder>() {

    // Her bir satırın view'larını tutan ViewHolder sınıfı
    inner class StreetViewHolder(private val binding: ItemStreetFilterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(streetName: String) {
            // TextView'e sokak adını yazdır
            binding.tvStreetName.text = streetName
            // Satırın tamamına tıklama olayı ekle
            itemView.setOnClickListener {
                // MainActivity'ye hangi sokağın seçildiğini bildir
                onStreetClicked(streetName)
            }
        }
    }

    // Yeni bir ViewHolder (satır) oluşturulduğunda çağrılır
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreetViewHolder {
        val binding = ItemStreetFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StreetViewHolder(binding)
    }

    // Bir satır ekrana geldiğinde verileri bağlamak için çağrılır
    override fun onBindViewHolder(holder: StreetViewHolder, position: Int) {
        holder.bind(streetList[position])
    }

    // Listedeki toplam eleman sayısını döndürür
    override fun getItemCount(): Int {
        return streetList.size
    }
}