package com.serkantken.secuasist.views.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.adapters.CallingContactAdapter
import com.serkantken.secuasist.adapters.CallingVillaAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.ActivityCallingBinding
import com.serkantken.secuasist.models.Cargo
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.DisplayableVilla
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaCallingState
import com.serkantken.secuasist.utils.Tools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.text.format

class CallingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallingBinding
    private lateinit var db: AppDatabase
    private lateinit var callingVillaAdapter: CallingVillaAdapter
    private lateinit var callingContactAdapter: CallingContactAdapter

    private var displayableVillas = mutableListOf<DisplayableVilla>()
    private var contactsForSelectedVillaMap = mutableMapOf<Int, List<Contact>>()

    private var currentSelectedVilla: DisplayableVilla? = null
    private var currentlySelectedContactFromAdapter: Contact? = null
    private var villasMap = mapOf<Int, Villa>()
    private val CALL_PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        window.isNavigationBarContrastEnforced = false
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top + Tools(this).convertDpToPixel(5), 0, 0)
            binding.rvVillaList.setPadding(
                + Tools(this).convertDpToPixel(12),
                systemBars.top + Tools(this).convertDpToPixel(55),
                + Tools(this).convertDpToPixel(12),
                systemBars.bottom + Tools(this).convertDpToPixel(200)
            )
            val marginparams = binding.blurContactCallLayout.layoutParams as ViewGroup.MarginLayoutParams
            marginparams.setMargins(Tools(this).convertDpToPixel(16), 0, Tools(this).convertDpToPixel(16), systemBars.bottom)
            binding.blurContactCallLayout.layoutParams = marginparams
            insets
        }

        Tools(this).blur(arrayOf(binding.blurToolbarButtons, binding.blurContactCallLayout), 10f, true)

        if (!Hawk.isBuilt()) {
            Hawk.init(this).build()
        }
        db = AppDatabase.getDatabase(this)

        setupRecyclerViews()
        setupActionButtons()
        requestPhonePermissionIfNeeded()

        val cargoList = intent.getSerializableExtra("CARGO_LIST") as? ArrayList<Cargo>
        if (cargoList != null && cargoList.isNotEmpty())
            loadInitialData(cargoList)
        else
            finishActivityWithMessage("Gerekli kargo bilgileri eksik veya geçersiz.")
    }

    private fun requestPhonePermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALL_PERMISSION_REQUEST_CODE) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Arama yapabilmek için telefon izni gereklidir.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadInitialData(initialCargoList: List<Cargo>) {
        val cargoIds = initialCargoList.map { it.cargoId }
        val villaIds = initialCargoList.map { it.villaId }.distinct()

        lifecycleScope.launch {
            // Sadece kargo listesini dinle. Bu bizim tek "doğruluk kaynağımız" olacak.
            db.cargoDao().getCargosByIdsAsFlow(cargoIds).collectLatest { updatedCargos ->

                // Değişiklik geldiğinde, ilişkili diğer verileri (değişmeyen) tek seferde çekelim.
                villasMap = db.villaDao().getVillasByIds(villaIds).associateBy { it.villaId }

                contactsForSelectedVillaMap.clear()
                villasMap.values.forEach { villa ->
                    val owners = db.villaContactDao().getRealOwnersForVillaNonFlow(villa.villaId)
                    val contacts = if (owners.isNotEmpty()) owners else db.villaContactDao().getContactsForVillaNonFlow(villa.villaId)
                    contactsForSelectedVillaMap[villa.villaId] = contacts
                }

                val oldInProgressCargoId = displayableVillas.find { it.state == VillaCallingState.IN_PROGRESS }?.villa?.villaId

                val newDisplayableVillas = updatedCargos.mapNotNull { cargo ->
                    val villaForThisCargo = villasMap[cargo.villaId]
                    if (villaForThisCargo == null) {
                        return@mapNotNull null // Eğer kargonun villası bulunamazsa listeye ekleme
                    }

                    // Arayüzdeki satırın durumunu SADECE bu kargonun durumuna göre belirle
                    val state: VillaCallingState = if (cargo.isCalled == 1) {
                        if (cargo.isMissed == 1) VillaCallingState.CALLED_FAILED else VillaCallingState.CALLED_SUCCESS
                    } else {
                        // Eğer bu kargo, daha önceki 'IN_PROGRESS' kargo ise, durumunu koru
                        if (cargo.cargoId == oldInProgressCargoId) VillaCallingState.IN_PROGRESS else VillaCallingState.PENDING
                    }

                    val ownerName = contactsForSelectedVillaMap[villaForThisCargo.villaId]?.firstOrNull()?.contactName ?: villaForThisCargo.villaNotes ?: "Sahibi Bilinmiyor"

                    DisplayableVilla(
                        cargoId = cargo.cargoId,
                        villa = villaForThisCargo,
                        ownerName = ownerName,
                        state = state
                    )
                }.sortedBy { it.villa.villaNo } // Villa numarasına göre sırala

                displayableVillas.clear()
                displayableVillas.addAll(newDisplayableVillas)

                // Eğer listede hiç 'IN_PROGRESS' durumunda olan kalmadıysa (örn: ilk açılışta veya aktif olan silindiğinde),
                // 'PENDING' durumundaki ilk villayı 'IN_PROGRESS' yap.
                if (displayableVillas.isNotEmpty() && displayableVillas.none { it.state == VillaCallingState.IN_PROGRESS }) {
                    displayableVillas.firstOrNull { it.state == VillaCallingState.PENDING }?.state = VillaCallingState.IN_PROGRESS
                }

                // RecyclerView'ı yeni listeyle güncelle
                callingVillaAdapter.submitList(displayableVillas.toList())

                // Aktif olan 'currentSelectedVilla'yı yeniden ayarla
                currentSelectedVilla = displayableVillas.firstOrNull { it.state == VillaCallingState.IN_PROGRESS }

                // Seçili villaya göre kişi listesini ana thread'de güncelle
                withContext(Dispatchers.Main) {
                    updateContactsForSelectedVilla()
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        callingVillaAdapter = CallingVillaAdapter { clickedDisplayableVilla ->
            handleVillaClick(clickedDisplayableVilla)
        }
        binding.rvVillaList.apply {
            layoutManager = LinearLayoutManager(this@CallingActivity)
            clipToPadding = false
            adapter = callingVillaAdapter
        }

        callingContactAdapter = CallingContactAdapter { selectedContact ->
            handleContactSelected(selectedContact)
        }
        binding.rvContacts.apply {
            layoutManager = LinearLayoutManager(this@CallingActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = callingContactAdapter
        }
    }

    private fun setupActionButtons() {
        binding.btnClose.setOnClickListener {
            finish()
        }

        binding.btnCall.setOnClickListener {
            // ... (önceki btnCall içeriği buraya gelecek, bir değişiklik yok) ...
            if (currentSelectedVilla == null) {
                Toast.makeText(this, "Lütfen önce bir villa seçin.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentlySelectedContactFromAdapter == null) {
                Toast.makeText(this, "Lütfen aranacak bir kişi seçin.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Arama izni verilmemiş. Lütfen izin verin.", Toast.LENGTH_LONG).show()
                requestPhonePermissionIfNeeded()
                return@setOnClickListener
            }

            val phoneNumber = currentlySelectedContactFromAdapter?.contactPhone
            if (phoneNumber.isNullOrBlank()) {
                Toast.makeText(this, "Seçili kişinin telefon numarası bulunmuyor.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Kargo takibi için arama logu oluşturma veya kargo durumunu güncelleme (opsiyonel)
            // Örnek: currentSelectedVilla'nın villaId'si ve companyId (eğer biliniyorsa) ile
            // ilgili kargonun arandığını veritabanına kaydet.
            // Bu kısım projenizin detaylı iş mantığına göre eklenebilir.

            val callIntent = Intent(Intent.ACTION_CALL, "tel:$phoneNumber".toUri())
            try {
                startActivity(callIntent)
                updateVillaState(currentSelectedVilla, VillaCallingState.CALLED)
                lifecycleScope.launch {
                    delay(2000)
                    showCallResultDialog()
                }
            } catch (e: SecurityException) {
                Toast.makeText(this, "Arama başlatılamadı. İzinleri kontrol edin.", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnCallFromHome.setOnClickListener {
            if (currentSelectedVilla == null) {
                Toast.makeText(this, "Lütfen önce bir villa seçin.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            //Toast.makeText(this, "${currentSelectedVilla?.villa?.villaNo} nolu villa evden arandı olarak işaretlendi.", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                logCargoInteraction(currentSelectedVilla?.cargoId, outcomeSuccessful = true)
            }
            updateVillaState(currentSelectedVilla, VillaCallingState.CALLED_SUCCESS) // Örnek durum
            // İlgili kargonun durumunu DB'de güncellemek gerekebilir.
            moveToNextPendingVilla()
        }

        binding.btnSkip.setOnClickListener {
            if (currentSelectedVilla == null) {
                Toast.makeText(this, "Atlanacak bir villa seçili değil.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            //Toast.makeText(this, "${currentSelectedVilla?.villa?.villaNo} nolu villa atlandı.", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                logCargoInteraction(currentSelectedVilla?.cargoId, outcomeSuccessful = false, wasSkipped = true)
            }
            updateVillaState(currentSelectedVilla, VillaCallingState.CALLED_FAILED) // Örnek durum
            // İlgili kargonun durumunu DB'de güncellemek gerekebilir.
            moveToNextPendingVilla()
        }
    }

    private fun showCallResultDialog() {
        if (currentSelectedVilla == null) {
            return
        }

        val villaNo = currentSelectedVilla!!.villa.villaNo
        AlertDialog.Builder(this)
            .setTitle("Arama Sonucu: $villaNo")
            .setMessage("$villaNo numaralı villa ile yapılan görüşme sonucunu belirtin:")
            .setPositiveButton("Ulaşıldı") { dialog, _ ->
                lifecycleScope.launch {
                    logCargoInteraction(currentSelectedVilla?.cargoId, outcomeSuccessful = true)
                }
                updateVillaState(currentSelectedVilla, VillaCallingState.CALLED_SUCCESS)
                moveToNextPendingVilla()
                dialog.dismiss()
            }
            .setNegativeButton("Ulaşılamadı") { dialog, _ ->
                lifecycleScope.launch {
                    logCargoInteraction(currentSelectedVilla?.cargoId, outcomeSuccessful = false)
                }
                updateVillaState(currentSelectedVilla, VillaCallingState.CALLED_FAILED)
                moveToNextPendingVilla()
                dialog.dismiss()
            }
            .setNeutralButton("Tekrar Ara") { dialog, _ ->
                dialog.dismiss() // Önce diyaloğu kapat

                if (currentlySelectedContactFromAdapter == null) {
                    Toast.makeText(this, "Aranacak kişi seçili değil.", Toast.LENGTH_SHORT).show()
                    // Bu durumda villanın durumunu IN_PROGRESS'e geri çekmek mantıklı olabilir                    // ki kullanıcı yeni bir kişi seçebilsin veya farklı bir işlem yapabilsin.
                    updateVillaState(currentSelectedVilla, VillaCallingState.IN_PROGRESS)
                    return@setNeutralButton
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Arama izni verilmemiş. Lütfen izin verin.", Toast.LENGTH_LONG).show()
                    requestPhonePermissionIfNeeded()
                    // İzin istendikten sonra villanın durumunu CALLED olarak bırakmak mantıklı,
                    // onResume tekrar tetiklenip diyaloğu açabilir veya kullanıcı izin verince manuel dener.
                    // updateVillaState(currentSelectedVilla, VillaCallingState.CALLED) // Zaten CALLED olmalı
                    return@setNeutralButton
                }

                val phoneNumber = currentlySelectedContactFromAdapter?.contactPhone
                if (phoneNumber.isNullOrBlank()) {
                    Toast.makeText(this, "Seçili kişinin telefon numarası bulunmuyor.", Toast.LENGTH_SHORT).show()
                    updateVillaState(currentSelectedVilla, VillaCallingState.IN_PROGRESS) // Kişi seçimi için
                    return@setNeutralButton
                }

                val callIntent = Intent(Intent.ACTION_CALL, "tel:$phoneNumber".toUri())
                try {
                    startActivity(callIntent)
                    // Durumu tekrar CALLED olarak ayarla ki onResume'da bu durum tekrar yakalanabilsin.
                    // Bu zaten mevcut durum olmalı ama garantiye alalım.
                    updateVillaState(currentSelectedVilla, VillaCallingState.CALLED)
                    lifecycleScope.launch {
                        delay(2000)
                        showCallResultDialog()
                    }
                } catch (e: SecurityException) {
                    Toast.makeText(this, "Arama başlatılamadı. İzinleri kontrol edin.", Toast.LENGTH_LONG).show()
                    // Arama başarısız olursa, IN_PROGRESS'e geri dönmek mantıklı olabilir.
                    updateVillaState(currentSelectedVilla, VillaCallingState.IN_PROGRESS)
                }
            }
            .setCancelable(false) // Kullanıcının bir seçim yapmasını zorunlu kıl
            .show()
    }

    private fun handleVillaClick(clickedDisplayableVilla: DisplayableVilla) {
        if (clickedDisplayableVilla.state == VillaCallingState.IN_PROGRESS) return

        val previousInProgressVilla = displayableVillas.find { it.state == VillaCallingState.IN_PROGRESS }
        previousInProgressVilla?.let {
            // Eğer önceki villa zaten CALLED, SUCCESS, FAILED gibi bir sonuca ulaşmadıysa PENDING yap.
            if (it.state == VillaCallingState.IN_PROGRESS || it.state == VillaCallingState.PENDING) {
                it.state = VillaCallingState.PENDING
            }
            val previousIndex = displayableVillas.indexOf(it)
            if (previousIndex != -1) {
                callingVillaAdapter.notifyItemChanged(previousIndex)
            }
        }

        clickedDisplayableVilla.state = VillaCallingState.IN_PROGRESS
        currentSelectedVilla = clickedDisplayableVilla
        val clickedIndex = displayableVillas.indexOf(clickedDisplayableVilla)
        if (clickedIndex != -1) {
            callingVillaAdapter.notifyItemChanged(clickedIndex)
            binding.rvVillaList.smoothScrollToPosition(clickedIndex)
        }

        // Seçilen villanın kişilerini yükle/göster
        val contacts = contactsForSelectedVillaMap[clickedDisplayableVilla.villa.villaId] ?: emptyList()
        callingContactAdapter.differ.submitList(contacts)
        if (contacts.isNotEmpty()) {
            currentlySelectedContactFromAdapter = contacts.first()
            callingContactAdapter.selectedPosition = 0
            callingContactAdapter.notifyItemChanged(0) // Adapter içindeki seçimi güncelle
            scrollToCenter(0, binding.rvContacts)
        } else {
            currentlySelectedContactFromAdapter = null
            callingContactAdapter.selectedPosition = -1
        }
        updateContactsForSelectedVilla()
    }

    private fun handleContactSelected(selectedContact: Contact) {
        this.currentlySelectedContactFromAdapter = selectedContact
        // Adapter zaten seçimi görsel olarak güncelliyor (`notifyItemChanged` ile)
        // İsteğe bağlı olarak seçilen kişiyi RecyclerView'da ortala
        val position = callingContactAdapter.differ.currentList.indexOf(selectedContact)
        if (position != -1) {
            scrollToCenter(position, binding.rvContacts)
        }
    }

    private fun updateContactsForSelectedVilla() {
        val contacts = currentSelectedVilla?.let {
            contactsForSelectedVillaMap[it.villa.villaId]
        } ?: emptyList()

        callingContactAdapter.differ.submitList(contacts)

        if (contacts.isNotEmpty()) {
            val oldSelection = currentlySelectedContactFromAdapter
            // Eğer eski seçili kişi yeni listede varsa onu seç, yoksa ilkini seç.
            val newSelectionPosition = if (oldSelection != null && contacts.contains(oldSelection)) {
                contacts.indexOf(oldSelection)
            } else {
                0
            }

            currentlySelectedContactFromAdapter = contacts[newSelectionPosition]
            callingContactAdapter.selectedPosition = newSelectionPosition
            scrollToCenter(newSelectionPosition, binding.rvContacts)
        } else {
            currentlySelectedContactFromAdapter = null
            callingContactAdapter.selectedPosition = -1
        }
        callingContactAdapter.notifyDataSetChanged() // Seçim değişikliğini yansıtmak için
    }

    private fun updateVillaState(villaToUpdate: DisplayableVilla?, newState: VillaCallingState) {
        villaToUpdate?.let {
            it.state = newState
            val index = displayableVillas.indexOf(it)
            if (index != -1) {
                callingVillaAdapter.notifyItemChanged(index)
            }
            // Burada ilgili kargoların veritabanındaki durumlarını da güncellemek isteyebilirsiniz.
            // Örneğin, bu villaya ait ve henüz aranmamış kargoların `isCalled`, `isMissed`, `callDate` alanları.
            // Bu, projenizin kargo takip mantığına göre detaylandırılmalıdır.
            // Örnek:
            lifecycleScope.launch {
                val cargosToUpdate = db.cargoDao().getUncalledCargosForVillaAsList(it.villa.villaId)
                cargosToUpdate.forEach { cargo ->
                    cargo.isCalled = if (newState == VillaCallingState.CALLED_SUCCESS || newState == VillaCallingState.CALLED) 1 else 0
                    cargo.isMissed = if (newState == VillaCallingState.CALLED_FAILED) 1 else 0
                    cargo.callDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date()) // Örnek tarih formatı
                    db.cargoDao().update(cargo)
                    (application as SecuAsistApplication).sendUpsert(cargo)
                }
            }
        }
    }

    private fun moveToNextPendingVilla() {
        val currentInProgressIndex = displayableVillas.indexOfFirst { it.state == VillaCallingState.IN_PROGRESS || it.state == VillaCallingState.CALLED_FAILED || it.state == VillaCallingState.CALLED_SUCCESS }

        var nextPendingVilla: DisplayableVilla? = null
        if (currentInProgressIndex != -1 && currentInProgressIndex + 1 < displayableVillas.size) {
            for (i in (currentInProgressIndex + 1) until displayableVillas.size) {
                if (displayableVillas[i].state == VillaCallingState.PENDING) {
                    nextPendingVilla = displayableVillas[i]
                    break
                }
            }
        }

        if (nextPendingVilla == null) { // Baştan ara
            nextPendingVilla = displayableVillas.firstOrNull { it.state == VillaCallingState.PENDING }
        }

        if (nextPendingVilla != null) {
            handleVillaClick(nextPendingVilla)
        } else {
            Toast.makeText(this, "Tüm villalar işlendi.", Toast.LENGTH_LONG).show()
            lifecycleScope.launch {
                delay(3000)
                if (this@CallingActivity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    finish()
                }
            }
        }
    }

    private suspend fun logCargoInteraction(
        cargoIdToLog: Int?,
        outcomeSuccessful: Boolean,
        wasSkipped: Boolean = false
    ) {
        val TAG_SYNC = "CargoSync" // Logları kolayca filtrelemek için özel bir tag

        if (cargoIdToLog == null) {
            Log.e(TAG_SYNC, "Adım 1 BAŞARISIZ: cargoId null olduğu için işlem iptal edildi.")
            return
        }

        Log.d(TAG_SYNC, "Adım 1: logCargoInteraction başladı. Cargo ID: $cargoIdToLog")

        try {
            val cargo = db.cargoDao().getCargoById(cargoIdToLog)
            if (cargo == null) {
                Log.e(TAG_SYNC, "Adım 2 BAŞARISIZ: Kargo (ID: $cargoIdToLog) yerel veritabanında bulunamadı.")
                return
            }
            Log.d(TAG_SYNC, "Adım 2: Kargo yerel veritabanında bulundu: ${cargo.cargoId}")

            // ... (Mevcut kargo güncelleme mantığınız aynı kalıyor)
            val newAttemptCount = if (wasSkipped) cargo.callAttemptCount else cargo.callAttemptCount + 1
            val callDateString = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
            val contactCalledId = currentlySelectedContactFromAdapter?.contactId
            val deviceName = Hawk.get("device_name", "Bilinmiyor")
            val finalIsCalled: Int
            val finalIsMissed: Int
            if (wasSkipped) {
                finalIsCalled = 0
                finalIsMissed = 1
            } else if (outcomeSuccessful) {
                finalIsCalled = 1
                finalIsMissed = 0
            } else {
                finalIsCalled = 1
                finalIsMissed = 1
            }
            val updatedCargo = cargo.copy(
                isCalled = finalIsCalled,
                isMissed = finalIsMissed,
                callDate = callDateString,
                callAttemptCount = newAttemptCount,
                whoCalled = contactCalledId,
                callingDeviceName = deviceName
            )

            // Adım 3: Yerel veritabanını güncelle
            db.cargoDao().update(updatedCargo)
            Log.d(TAG_SYNC, "Adım 3: Kargo (ID: ${updatedCargo.cargoId}) yerel veritabanında güncellendi.")

            // Adım 4: Sunucuya gönderme işlemini başlat
            Log.d(TAG_SYNC, "Adım 4: Senkronizasyon için SecuAsistApplication'a gönderiliyor...")
            (application as SecuAsistApplication).sendUpsert(updatedCargo)
            Log.i(TAG_SYNC, "Adım 5: sendUpsert fonksiyonu başarıyla çağrıldı. Kargo ID: ${updatedCargo.cargoId}")

        } catch (e: Exception) {
            Log.e(TAG_SYNC, "logCargoInteraction içinde BEKLENMEDİK HATA: ${e.message}", e)
        }
    }

    private fun scrollToCenter(position: Int, recyclerView: RecyclerView) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        layoutManager?.let {
            val firstVisibleItemPosition = it.findFirstVisibleItemPosition()
            val lastVisibleItemPosition = it.findLastVisibleItemPosition()
            val visibleItems = lastVisibleItemPosition - firstVisibleItemPosition + 1

            if (position < firstVisibleItemPosition || position > lastVisibleItemPosition) {
                it.scrollToPositionWithOffset(position, recyclerView.width / 2 - (recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.width ?: 0) / 2)
            } else {
                val currentItemView = it.findViewByPosition(position)
                val itemWidth = currentItemView?.width ?: (recyclerView.width / visibleItems) // Fallback
                val targetScrollX = (itemWidth * position) - (recyclerView.width / 2) + (itemWidth / 2)
                //recyclerView.smoothScrollBy(targetScrollX - it.getOffsetToStart(), 0)
            }
        }
    }

    private fun finishActivityWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}