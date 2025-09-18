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
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.adapters.CallingContactAdapter
import com.serkantken.secuasist.adapters.CallingVillaAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.ActivityCallingBinding
import com.serkantken.secuasist.models.Cargo
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.DisplayableVilla
import com.serkantken.secuasist.models.VillaCallingState
import com.serkantken.secuasist.utils.Tools
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.text.format

class CallingActivity : AppCompatActivity() {
    private companion object {
        const val TAG = "CallingActivity"
    }

    private lateinit var binding: ActivityCallingBinding
    private lateinit var db: AppDatabase
    private lateinit var callingVillaAdapter: CallingVillaAdapter
    private lateinit var callingContactAdapter: CallingContactAdapter

    private var displayableVillas = mutableListOf<DisplayableVilla>()
    // private var allContacts = mutableListOf<Contact>() // Bu artık kullanılmayacak
    private var contactsForSelectedVillaMap = mutableMapOf<Int, List<Contact>>()

    private var currentSelectedVilla: DisplayableVilla? = null
    private var currentlySelectedContactFromAdapter: Contact? = null

    // private val gson = Gson() // Gerekmiyorsa kaldırılabilir
    private val CALL_PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
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

        // Intent'ten kargo listesini al
        val cargoList = intent.getSerializableExtra("CARGO_LIST") as? ArrayList<Cargo>

        if (cargoList != null && cargoList.isNotEmpty()) {
            val targetVillaIds = cargoList.map { it.villaId }.distinct()
            loadInitialData(targetVillaIds, cargoList)
        } else {
            finishActivityWithMessage("Gerekli kargo bilgileri eksik veya geçersiz.")
        }
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

    private fun loadInitialData(targetVillaIds: List<Int>, cargoList: ArrayList<Cargo>?) {
        lifecycleScope.launch {
            try {
                val allVillasFromDb = db.villaDao().getAllVillasAsList()

                val filteredVillas = allVillasFromDb.filter { it.villaId in targetVillaIds }

                if (filteredVillas.isEmpty()) {
                    finishActivityWithMessage("Kargolara ait gösterilecek villa bulunamadı.")
                    return@launch
                }

                if (cargoList == null) { // Ekstra güvenlik kontrolü
                    finishActivityWithMessage("Kargo listesi yüklenemedi.")
                    return@launch
                }

                displayableVillas.clear()
                contactsForSelectedVillaMap.clear()

                // Villaların kişilerini ve DisplayableVilla nesnelerini oluştur
                val deferreds = filteredVillas.map { villa ->
                    async { // Her villa için asenkron işlem
                        var contactsForVilla =
                            db.villaContactDao().getRealOwnersForVillaNonFlow(villa.villaId)
                        if (contactsForVilla.isEmpty()) {
                            contactsForVilla =
                                db.villaContactDao().getContactsForVillaNonFlow(villa.villaId)
                        }
                        contactsForSelectedVillaMap[villa.villaId] = contactsForVilla

                        val ownerName =
                            contactsForVilla.firstOrNull()?.contactName ?: villa.villaNotes
                            ?: "Sahibi Bilinmiyor"

                        // Villa ile ilişkili kargoyu bul ve cargoId'yi al
                        // filteredVillas, cargoList'teki villaId'lere göre oluşturulduğu için
                        // her zaman bir eşleşme olmalı.
                        val associatedCargo = cargoList.find { it.villaId == villa.villaId }
                        if (associatedCargo == null) {
                            null
                        } else {
                            val cargoId = associatedCargo.cargoId
                            // Tüm villaları önce PENDING olarak oluştur
                            DisplayableVilla(cargoId, villa, ownerName, VillaCallingState.PENDING)
                        }
                    }
                }
                // Tüm asenkron işlemleri bekle, null olmayan sonuçları al ve villaNo'ya göre sırala
                val tempDisplayableVillas =
                    deferreds.awaitAll().filterNotNull().sortedBy { it.villa.villaNo }

                displayableVillas.addAll(tempDisplayableVillas)

                // Sıralanmış listenin ilk elemanını IN_PROGRESS yap (eğer liste boş değilse)
                if (displayableVillas.isNotEmpty()) {
                    displayableVillas.first().state = VillaCallingState.IN_PROGRESS
                }

                // Villa adapter'ını güncelle
                callingVillaAdapter.submitList(displayableVillas.toList())

                // Başlangıçta seçili olan (IN_PROGRESS) villayı bul
                currentSelectedVilla =
                    displayableVillas.firstOrNull() // firstOrNull() zaten IN_PROGRESS olanı (ilkini) verecektir.

                if (currentSelectedVilla != null) {
                    val initialContacts =
                        contactsForSelectedVillaMap[currentSelectedVilla!!.villa.villaId] ?: emptyList()
                    callingContactAdapter.differ.submitList(initialContacts)
                    if (initialContacts.isNotEmpty()) {
                        currentlySelectedContactFromAdapter = initialContacts.first()
                        callingContactAdapter.selectedPosition = 0
                        callingContactAdapter.notifyItemChanged(0)
                        scrollToCenter(0, binding.rvContacts)
                    } else {
                        currentlySelectedContactFromAdapter = null
                        callingContactAdapter.selectedPosition = -1 // Seçim olmadığını belirt
                    }
                } else {
                    callingContactAdapter.differ.submitList(emptyList()) // Kişi listesini temizle
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@CallingActivity,
                    "Veri yüklenirken bir hata oluştu: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
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
            Toast.makeText(this, "${currentSelectedVilla?.villa?.villaNo} nolu villa evden arandı olarak işaretlendi.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "${currentSelectedVilla?.villa?.villaNo} nolu villa atlandı.", Toast.LENGTH_SHORT).show()
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
            // lifecycleScope.launch {
            //     val cargosToUpdate = db.cargoDao().getUncalledCargosForVilla(it.villa.villaId)
            //     cargosToUpdate.forEach { cargo ->
            //         cargo.isCalled = if (newState == VillaCallingState.CALLED_SUCCESS || newState == VillaCallingState.CALLED) 1 else 0
            //         cargo.isMissed = if (newState == VillaCallingState.CALLED_FAILED) 1 else 0
            //         cargo.callDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date()) // Örnek tarih formatı
            //         db.cargoDao().update(cargo)
            //     }
            // }
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
        if (cargoIdToLog == null) {
            Log.e(TAG, "Cannot log cargo interaction: cargoId is null.")
            return
        }

        val cargo = db.cargoDao().getCargoById(cargoIdToLog)
        if (cargo == null) {
            Log.e(TAG, "Cannot log cargo interaction: Cargo with id $cargoIdToLog not found in DB.")
            return
        }

        val newAttemptCount = if (wasSkipped) cargo.callAttemptCount else cargo.callAttemptCount + 1 // Atlanırsa deneme sayısı artmasın
        val callDateString = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC") // Standart UTC kullanalım
        }.format(Date())

        // "Atla" durumunda arayan kişi null olabilir veya farklı bir mantık gerekebilir.
        // Şimdilik, eğer bir kişi seçiliyse onu kullanalım.
        val contactCalledId = currentlySelectedContactFromAdapter?.contactId
        val deviceName = Hawk.get("device_name", "Bilinmiyor")

        val finalIsCalled: Int
        val finalIsMissed: Int

        if (wasSkipped) {
            finalIsCalled = 0 // Arama yapılmadı
            finalIsMissed = 1 // Teslimat atlandı/başarısız
        } else if (outcomeSuccessful) {
            finalIsCalled = 1 // Arama yapıldı/işlem başarılı
            finalIsMissed = 0
        } else { // Başarısız arama/işlem
            finalIsCalled = 1
            finalIsMissed = 1
        }

        val updatedCargo = cargo.copy(
            isCalled = finalIsCalled,
            isMissed = finalIsMissed,
            callDate = callDateString, // Her etkileşimde callDate güncellenir
            callAttemptCount = newAttemptCount,
            whoCalled = contactCalledId, // Atla durumunda bu null olabilir, DB'de nullable olmalı
            callingDeviceName = deviceName
        )

        db.cargoDao().update(updatedCargo)
        Log.d(TAG, "Cargo record updated for cargoId: $cargoIdToLog. OutcomeSuccessful: $outcomeSuccessful, Skipped: $wasSkipped, NewAttemptCount: $newAttemptCount")
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