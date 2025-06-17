package com.serkantken.secuasist

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.serkantken.secuasist.adapters.VillaAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.ActivityMainBinding
import com.serkantken.secuasist.databinding.DialogAddEditVillaBinding
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.network.VillaDto
import com.serkantken.secuasist.network.WebSocketClient
import com.serkantken.secuasist.network.WebSocketMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var villaAdapter: VillaAdapter // Adaptör
    private lateinit var appDatabase: AppDatabase // Room Database

    private var activityScopeJob: Job? = null
    private val activityScope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Main + (activityScopeJob ?: Job()))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Veritabanı instance'ını al
        appDatabase = AppDatabase.getDatabase(this)

        // Application sınıfı üzerinden WebSocketClient instance'ına erişim
        webSocketClient = (application as SecuAsistApplication).webSocketClient

        setupRecyclerView() // RecyclerView'ı kur
        setupListeners()    // Buton dinleyicilerini kur
        observeVillas()     // Villaları gözlemlemeye başla
        observeWebSocketMessages() // WebSocket mesajlarını gözlemlemeye başla

        // Arama kutusuna yazıldığında filtreleme mantığı (ileride eklenebilir)
        binding.etSearch.setOnClickListener {
            showToast("Arama çubuğu tıklandı!")
            // Gerçek arama mantığı burada olacak
        }
    }

    private fun setupRecyclerView() {
        villaAdapter = VillaAdapter(
            onItemClick = { villa ->
                // Villa öğesine tıklandığında (düzenleme veya detay)
                showToast("Villa ${villa.villaNo} tıklandı.")
                showAddEditVillaDialog(villa) // Düzenleme modunda dialogu aç
            },
            onItemLongClick = { villa ->
                // Villa öğesine uzun basıldığında (düzenleme veya silme)
                showToast("Villa ${villa.villaNo} uzun tıklandı.")
                showAddEditVillaDialog(villa) // Düzenleme modunda dialogu aç
                true // Olayı işledik
            }
        )
        binding.recyclerViewVillas.apply {
            adapter = villaAdapter
        }
    }

    private fun setupListeners() {
        // FAB (Floating Action Button) dinleyicisi - Yeni Villa Ekle
        binding.fabAddVilla.setOnClickListener {
            showAddEditVillaDialog(null) // Ekleme modunda dialogu aç (null villa ile)
        }

        // Üstteki butonların dinleyicileri (şu an sadece Toast)
        binding.btnBack.setOnClickListener {
            showToast("Geri butonu tıklandı.")
            // finish() // Bir önceki ekrana dönmek için kullanılabilir
        }
        binding.btnFilter.setOnClickListener {
            showToast("Filtre butonu tıklandı.")
            // Filtreleme/Sıralama dialogu açılabilir
        }
    }

    // Villaları Room veritabanından çek ve RecyclerView'a gönder
    private fun observeVillas() {
        activityScope.launch {
            appDatabase.villaDao().getAllVillas().collect { villas ->
                villaAdapter.submitList(villas)
                showToast("Villalar güncellendi: ${villas.size} adet.")
            }
        }
    }

    // WebSocket mesajlarını dinle (Bağlantı durumları ve sunucudan gelen diğer mesajlar)
    private fun observeWebSocketMessages() {
        activityScope.launch {
            webSocketClient.incomingMessages.collect { message ->
                if (message.startsWith("STATUS:")) {
                    when (message) {
                        "STATUS:CONNECTED" -> showToast("WebSocket Bağlandı.")
                        "STATUS:DISCONNECTED" -> showToast("WebSocket Bağlantısı Kesildi. Yeniden bağlanıyor...")
                        "STATUS:DISCONNECTING" -> showToast("WebSocket Bağlantısı Kapanıyor...")
                        "STATUS:ERROR" -> showToast("WebSocket Hatası Oluştu. Yeniden bağlanıyor...")
                        else -> showToast("WebSocket Durum: ${message.substringAfter("STATUS:")}")
                    }
                } else {
                    // Sunucudan gelen normal mesajları burada işleyebiliriz
                    // Örneğin: {"type": "villa_added_success", "villaNo": 101}
                    // JSON'ı parse edip ilgili aksiyonu alabiliriz.
                    showToast("Sunucudan gelen mesaj: $message")
                }
            }
        }
    }

    // Villa Ekleme/Düzenleme AlertDialog'unu gösteren metod
    private fun showAddEditVillaDialog(villaToEdit: Villa?) {
        val dialogBinding = DialogAddEditVillaBinding.inflate(layoutInflater)
        val isEditMode = villaToEdit != null

        // Düzenleme modundaysa alanları doldur
        if (isEditMode) {
            dialogBinding.etVillaNo.setText(villaToEdit?.villaNo?.toString())
            dialogBinding.etVillaNotes.setText(villaToEdit?.villaNotes)
            dialogBinding.etVillaStreet.setText(villaToEdit?.villaStreet)
            dialogBinding.etVillaNavigation.setText(villaToEdit?.villaNavigation)
            dialogBinding.cbIsUnderConstruction.isChecked = villaToEdit?.isVillaUnderConstruction == 1
            dialogBinding.cbIsSpecial.isChecked = villaToEdit?.isVillaSpecial == 1
            dialogBinding.cbIsRental.isChecked = villaToEdit?.isVillaRental == 1
            dialogBinding.cbIsCallFromHome.isChecked = villaToEdit?.isVillaCallFromHome == 1
            dialogBinding.cbIsCallForCargo.isChecked = villaToEdit?.isVillaCallForCargo == 1
            dialogBinding.cbIsEmpty.isChecked = villaToEdit?.isVillaEmpty == 1

            // Düzenleme modunda villa numarasının değiştirilmesini engelle (isteğe bağlı)
            dialogBinding.etVillaNo.isEnabled = false
        }

        AlertDialog.Builder(this)
            .setTitle(if (isEditMode) "Villa Düzenle" else "Yeni Villa Ekle")
            .setView(dialogBinding.root) // Özel layout'u ayarla
            .setPositiveButton(if (isEditMode) "Güncelle" else "Ekle") { dialog, _ ->
                // Kullanıcı "Kaydet"e bastığında
                val villaNo = dialogBinding.etVillaNo.text.toString().toIntOrNull()
                if (villaNo == null) {
                    showToast("Villa Numarası geçerli değil.")
                    return@setPositiveButton
                }

                val newVilla = Villa(
                    villaId = villaToEdit?.villaId ?: 0, // Düzenleme modunda ID'yi koru
                    villaNo = villaNo,
                    villaNotes = dialogBinding.etVillaNotes.text.toString(),
                    villaStreet = dialogBinding.etVillaStreet.text.toString(),
                    villaNavigation = dialogBinding.etVillaNavigation.text.toString(),
                    isVillaUnderConstruction = if (dialogBinding.cbIsUnderConstruction.isChecked) 1 else 0,
                    isVillaSpecial = if (dialogBinding.cbIsSpecial.isChecked) 1 else 0,
                    isVillaRental = if (dialogBinding.cbIsRental.isChecked) 1 else 0,
                    isVillaCallFromHome = if (dialogBinding.cbIsCallFromHome.isChecked) 1 else 0,
                    isVillaCallForCargo = if (dialogBinding.cbIsCallForCargo.isChecked) 1 else 0,
                    isVillaEmpty = if (dialogBinding.cbIsEmpty.isChecked) 1 else 0
                )

                if (isEditMode) {
                    // Room'da güncelle
                    activityScope.launch {
                        appDatabase.villaDao().update(newVilla)
                        showToast("Villa ${newVilla.villaNo} güncellendi.")
                        // WebSocket üzerinden sunucuya güncelleme mesajı gönder
                        sendVillaAddOrUpdateToWebSocket(newVilla)
                    }
                } else {
                    // Room'a ekle
                    activityScope.launch {
                        val insertedId = appDatabase.villaDao().insert(newVilla)
                        if (insertedId > 0) {
                            showToast("Villa ${newVilla.villaNo} eklendi.")
                            // WebSocket üzerinden sunucuya ekleme mesajı gönder
                            sendVillaAddOrUpdateToWebSocket(newVilla.copy(villaId = insertedId.toInt())) // Room ID'sini de gönder
                        } else {
                            showToast("Villa eklenirken hata oluştu.")
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("İptal") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    // WebSocket üzerinden villa ekleme/güncelleme mesajı gönderme
    private fun sendVillaAddOrUpdateToWebSocket(villa: Villa) {
        val type = if (villa.villaId > 0) "update_villa" else "add_villa" // ID varsa güncelleme
        val villaDto = VillaDto(
            villaNo = villa.villaNo,
            villaNotes = villa.villaNotes,
            villaStreet = villa.villaStreet,
            villaNavigation = villa.villaNavigation,
            isVillaUnderConstruction = villa.isVillaUnderConstruction,
            isVillaSpecial = villa.isVillaSpecial,
            isVillaRental = villa.isVillaRental,
            isVillaCallFromHome = villa.isVillaCallFromHome,
            isVillaCallForCargo = villa.isVillaCallForCargo,
            isVillaEmpty = villa.isVillaEmpty
            // Contacts ve Cameras şimdilik boş kalacak, ayrı ayrı eklenecek
        )
        val message = Gson().toJson(WebSocketMessage(type = type, data = villaDto))
        webSocketClient.sendMessage(message)
    }

    override fun onStart() {
        super.onStart()
        // Gelen mesajları dinlemeye başla
        activityScopeJob = activityScope.launch {
            webSocketClient.incomingMessages.collect { message ->
                if (message.startsWith("STATUS:")) {
                    when (message) {
                        "STATUS:CONNECTED" -> showToast("WebSocket Bağlandı.")
                        "STATUS:DISCONNECTED" -> showToast("WebSocket Bağlantısı Kesildi. Yeniden bağlanıyor...")
                        "STATUS:DISCONNECTING" -> showToast("WebSocket Bağlantısı Kapanıyor...")
                        "STATUS:ERROR" -> showToast("WebSocket Hatası Oluştu. Yeniden bağlanıyor...")
                        else -> showToast("WebSocket Durum: ${message.substringAfter("STATUS:")}")
                    }
                } else {
                    // Sunucudan gelen normal mesajları burada işleyebiliriz
                    // Örneğin: {"type": "villa_added_success", "villaId": 123}
                    showToast("Sunucudan gelen mesaj: $message")
                    // Eğer sunucudan bir villa ekleme/güncelleme onayı gelirse,
                    // yerel Room'da da güncelleme yapabiliriz (ancak Room Flow zaten günceller).
                    // Belki de sadece kullanıcıya özel bir bildirim mesajı gösterilebilir.
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        activityScopeJob?.cancel()
        activityScopeJob = null
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}