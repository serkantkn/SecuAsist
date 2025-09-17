package com.serkantken.secuasist.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.serkantken.secuasist.R
import com.serkantken.secuasist.models.CargoReport
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CargoReportAdapter() : RecyclerView.Adapter<CargoReportAdapter.ReportViewHolder>() {

    // Adapter'ın hangi modda veri göstereceğini belirleyen enum
    enum class ReportMode {
        BY_COMPANY,
        BY_VILLA
    }

    // Aktiviteden değiştirilebilecek public bir değişken
    var currentMode: ReportMode = ReportMode.BY_COMPANY

    private val diffCallback = object : DiffUtil.ItemCallback<CargoReport>() {
        override fun areItemsTheSame(oldItem: CargoReport, newItem: CargoReport): Boolean {
            return oldItem.cargoId == newItem.cargoId
        }

        override fun areContentsTheSame(oldItem: CargoReport, newItem: CargoReport): Boolean {
            return oldItem == newItem
        }
    }
    val differ = AsyncListDiffer(this, diffCallback)

    // Artık tek bir layout olduğu için ViewType'a gerek yok.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun getItemCount() = differ.currentList.size

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(differ.currentList[position])
    }

    inner class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Layout'taki tüm TextView'ları tanımla
        private val mainIdentifier: TextView = itemView.findViewById(R.id.tv_content)
        private val callStatus: TextView = itemView.findViewById(R.id.tv_call_status)
        private val callDateTime: TextView = itemView.findViewById(R.id.tv_call_date)
        private val callDevice: TextView = itemView.findViewById(R.id.tv_call_device)
        private val whoCalled: TextView = itemView.findViewById(R.id.tv_who_called)

        fun bind(cargo: CargoReport) {
            // 1. Ortak Alanları Doldur
            val statusText = cargo.callStatus
            callStatus.text = statusText
            whoCalled.text = cargo.whoCalledName ?: "Bilinmeyen kişi"

            callDateTime.text = cargo.callDate?.let {
                try {
                    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                    parser.timeZone = TimeZone.getTimeZone("UTC")
                    val date = parser.parse(it)
                    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("tr"))
                    formatter.format(date)
                } catch (e: Exception) { "Tarih yok" }
            } ?: "Tarih yok"

            callDevice.text = cargo.callingDeviceName ?: "Bilinmeyen cihaz"

            // 2. Moda Göre Değişen Alanı Doldur
            when (currentMode) {
                ReportMode.BY_COMPANY -> {
                    // "Şirkete Göre" modunda, ana tanımlayıcı Villa Numarasıdır.
                    mainIdentifier.text = cargo.villaNo
                }
                ReportMode.BY_VILLA -> {
                    // "Villaya Göre" modunda, ana tanımlayıcı Şirket Adıdır.
                    mainIdentifier.text = cargo.companyName ?: "Bilinmeyen Şirket"
                }
            }
        }
    }
}