package com.serkantken.secuasist.views.activities

import android.R
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.adapters.ContactsAdapter
import com.serkantken.secuasist.adapters.MainViewPagerAdapter
import com.serkantken.secuasist.adapters.VillaAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.ActivityMainBinding
import com.serkantken.secuasist.databinding.DialogAddEditContactBinding
import com.serkantken.secuasist.databinding.DialogAddEditVillaBinding
import com.serkantken.secuasist.databinding.DialogManageVillaContactsBinding
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaContact
import com.serkantken.secuasist.network.ContactDto
import com.serkantken.secuasist.network.VillaContactDeleteDto
import com.serkantken.secuasist.network.VillaContactDto
import com.serkantken.secuasist.network.VillaDto
import com.serkantken.secuasist.network.WebSocketClient
import com.serkantken.secuasist.network.WebSocketMessage
import com.serkantken.secuasist.utils.Tools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var villaAdapter: VillaAdapter
    private lateinit var appDatabase: AppDatabase
    private val gson = Gson() // Gson instance'ı

    private var activityScopeJob: Job? = null
    private val activityScope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Main + (activityScopeJob ?: Job()))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top + Tools(this).convertDpToPixel(5), 0, 0)
            binding.bottomNavBar.setPadding(Tools(this).convertDpToPixel(16), 0, Tools(this).convertDpToPixel(16), systemBars.bottom)
            insets
        }

        Tools(this).blur(arrayOf(binding.blurSearchButton, binding.blurToolbarButtons, binding.blurMessage, binding.blurNavView), 10f, false)
        appDatabase = AppDatabase.Companion.getDatabase(this)
        webSocketClient = (application as SecuAsistApplication).webSocketClient

        setupViewPagerAndTabs()
        setupListeners()
        observeWebSocketMessages()

        binding.icSearch.setOnClickListener {
            showToast("Arama çubuğu tıklandı!")
        }
    }

    private fun setupViewPagerAndTabs() {
        binding.apply {
            viewPager.adapter = MainViewPagerAdapter(this@MainActivity)

            viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updateBottomNavState(position)
                    updateToolbarTitle(position)
                    updateMainActionButton(position)
                }
            })

            // Alt navigasyon sekmelerine tıklama dinleyicileri
            tabVillas.setOnClickListener {
                viewPager.currentItem = 0
                updateBottomNavState(0) // onPageSelected zaten çağrılacak
                updateToolbarTitle(0)
            }
            tabCargos.setOnClickListener {
                viewPager.currentItem = 1
                updateBottomNavState(1)
                updateToolbarTitle(1)
            }
            tabContacts.setOnClickListener {
                viewPager.currentItem = 2
                updateBottomNavState(2)
                updateToolbarTitle(2)
            }
            tabCameras.setOnClickListener {
                viewPager.currentItem = 3
                updateBottomNavState(3)
                updateToolbarTitle(3)
            }

            // Başlangıç durumu
            updateBottomNavState(0)
            updateToolbarTitle(0)
            updateMainActionButton(0)
        }

    }

    private fun updateMainActionButton(fragmentPosition: Int) {
        when (fragmentPosition) {
            0 -> { // VillaFragment
                binding.icSearch.setImageResource(com.serkantken.secuasist.R.drawable.ic_search) // Arama ikonu
                binding.icSearch.setOnClickListener {
                    showToast("Villa arama özelliği yakında...")
                    // TODO: Villa arama işlevini buraya ekle
                }
                binding.blurSearchButton.visibility = View.VISIBLE
            }
            1 -> { // CargoFragment
                binding.icSearch.setImageResource(com.serkantken.secuasist.R.drawable.ic_add) // Ekleme ikonu
                binding.icSearch.setOnClickListener {
                    showAddEditCargoCompanyDialog(null) // Yeni kargo şirketi ekleme diyalogunu aç
                }
                binding.blurSearchButton.visibility = View.VISIBLE
            }
            2 -> { // ContactsFragment
                binding.icSearch.setImageResource(com.serkantken.secuasist.R.drawable.ic_search) // Arama ikonu
                binding.icSearch.setOnClickListener {
                    showToast("Kişi arama özelliği yakında...")
                    // TODO: Kişi arama işlevini buraya ekle
                }
                binding.blurSearchButton.visibility = View.VISIBLE
            }
            3 -> { // CameraFragment
                binding.blurSearchButton.visibility = View.GONE
            }
            else -> {
                binding.blurSearchButton.visibility = View.GONE // Diğer durumlar için gizle
            }
        }
    }

    // Bu fonksiyonu daha sonra dolduracağız, şimdilik bir Toast ile test edebiliriz.
// showAddEditVillaDialog gibi `internal` veya `public` olmalı.
    internal fun showAddEditCargoCompanyDialog(companyToEdit: com.serkantken.secuasist.models.CargoCompany?) {
        // Gerçek diyalog gösterme kodu buraya gelecek.
        // Şimdilik test amaçlı bir Toast mesajı:
        val message = if (companyToEdit == null) {
            "Yeni Kargo Şirketi Ekleme Diyalogu Tetiklendi"
        } else {
            "Kargo Şirketi '${companyToEdit.companyName}' Düzenleme Diyalogu Tetiklendi"
        }
        showToast(message)

        // TODO: Gerçek diyalog açma kodunu buraya ekleyeceğiz.
        // Örnek:
        // val dialogBinding = DialogAddEditCargoCompanyBinding.inflate(layoutInflater)
        // val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        // dialogBinding.blurWindow.setupWith(findViewById(android.R.id.content)) // root view
        //     .setBlurRadius(10f)
        // ... (dialog içindeki elemanları ve butonları ayarla)
        // dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        // dialog.show()
    }

    private fun updateBottomNavState(selectedPosition: Int) {
        val tabs = listOf(binding.tabVillas, binding.tabCargos, binding.tabContacts, binding.tabCameras)

        tabs.forEachIndexed { index, tabLayout ->
            val isSelected = index == selectedPosition
            if (isSelected) {
                tabLayout.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_blur) // Seçili arka planı
            } else {
                tabLayout.background = null // Seçili olmayan için arka planı kaldır
            }
        }
    }

    private fun updateToolbarTitle(position: Int) {
        binding.contentTitle.text = when (position) {
            0 -> "Villa Listesi"
            1 -> "Kargo Yönetimi"
            2 -> "Kişi Listesi"
            3 -> "Kamera Listesi"
            else -> "SecuAsist"
        }
    }

    private fun setupListeners() {
        //binding.fabAddVilla.setOnClickListener {
        //    showAddEditVillaDialog(null)
        //}
        binding.btnFilter.setOnClickListener {
            showToast("Filtre butonu tıklandı.")
        }
        binding.btnMore.setOnClickListener {
            showAddEditVillaDialog(null)
        }
    }

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
                    showToast("Sunucudan gelen mesaj: $message")
                    // Burada sunucudan gelen spesifik mesajları işleyebiliriz,
                    // örneğin bir CRUD işlemi onayı.
                    // Şimdilik Room Flow zaten listeyi güncelleyecektir.
                }
            }
        }
    }

    //region Villa Ekleme/Düzenleme Dialogu
    fun showAddEditVillaDialog(villaToEdit: Villa?) {
        val dialogBinding = DialogAddEditVillaBinding.inflate(layoutInflater)
        val isEditMode = villaToEdit != null
        Tools(this).blur(arrayOf(dialogBinding.blurWindow), 10f, true)

        if (isEditMode) {
            dialogBinding.windowTitle.text = "Villa Düzenle"
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

            dialogBinding.etVillaNo.isEnabled = false // Düzenleme modunda villa numarasını kilitle
        } else {
            dialogBinding.windowTitle.text = "Yeni Villa Ekle"
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        alertDialog.setOnShowListener {
            val positiveButton = dialogBinding.btnSave
            positiveButton.setOnClickListener {
                val villaNo = dialogBinding.etVillaNo.text.toString().toIntOrNull()
                if (villaNo == null) {
                    showToast("Villa Numarası geçerli değil.")
                    return@setOnClickListener
                }

                val villa = Villa(
                    villaId = villaToEdit?.villaId ?: 0,
                    villaNo = villaNo,
                    villaNotes = dialogBinding.etVillaNotes.text.toString()
                        .takeIf { it.isNotBlank() },
                    villaStreet = dialogBinding.etVillaStreet.text.toString()
                        .takeIf { it.isNotBlank() },
                    villaNavigation = dialogBinding.etVillaNavigation.text.toString()
                        .takeIf { it.isNotBlank() },
                    isVillaUnderConstruction = if (dialogBinding.cbIsUnderConstruction.isChecked) 1 else 0,
                    isVillaSpecial = if (dialogBinding.cbIsSpecial.isChecked) 1 else 0,
                    isVillaRental = if (dialogBinding.cbIsRental.isChecked) 1 else 0,
                    isVillaCallFromHome = if (dialogBinding.cbIsCallFromHome.isChecked) 1 else 0,
                    isVillaCallForCargo = if (dialogBinding.cbIsCallForCargo.isChecked) 1 else 0,
                    isVillaEmpty = if (dialogBinding.cbIsEmpty.isChecked) 1 else 0
                )

                activityScope.launch {
                    if (isEditMode) {
                        appDatabase.villaDao().update(villa)
                        showToast("Villa ${villa.villaNo} güncellendi.")
                        sendVillaAddOrUpdateToWebSocket(villa)
                    } else {
                        val insertedId = appDatabase.villaDao().insert(villa)
                        if (insertedId > 0) {
                            showToast("Villa ${villa.villaNo} eklendi.")
                            sendVillaAddOrUpdateToWebSocket(villa.copy(villaId = insertedId.toInt()))
                        } else {
                            showToast("Villa eklenirken hata oluştu. Villa No zaten mevcut olabilir.")
                        }
                    }
                    alertDialog.dismiss()
                }
            }

            val neutralButton = dialogBinding.btnVillaContacts
            if (!isEditMode) {
                neutralButton.visibility = View.GONE
            }
            neutralButton.setOnClickListener {
                villaToEdit?.let {
                    showManageVillaContactsDialog(it)
                } ?: showToast("Villa eklenmeden kişileri yönetemezsiniz.")
                alertDialog.dismiss()
            }
            dialogBinding.btnClose.setOnClickListener {
                alertDialog.dismiss()
            }
        }
        alertDialog.window?.setBackgroundDrawableResource(R.color.transparent)
        alertDialog.show()
    }
    //endregion

    //region Kişi Yönetimi Dialogları
    private fun showManageVillaContactsDialog(villa: Villa) {
        val dialogBinding = DialogManageVillaContactsBinding.inflate(layoutInflater)
        val contactsAdapter = ContactsAdapter(
            onItemClick = { contact, _ ->
                showAddEditContactDialog(contact, villa.villaId)
            },
            onDeleteClick = { contact, _ ->
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("İlişkiyi Sil") // Başlığı "Kişi Sil" yerine "İlişkiyi Sil" olarak değiştirmek daha doğru olabilir
                    .setMessage("Villa ${villa.villaNo} için ${contact.contactName} adlı kişinin ilişkisini silmek istediğinize emin misiniz?")
                    .setPositiveButton("Evet, Sil") { dialog, _ ->
                        activityScope.launch {
                            try {
                                // 1. Villa-Kişi ilişkisini sil
                                appDatabase.villaContactDao()
                                    .deleteByVillaIdAndContactId(villa.villaId, contact.contactId)
                                sendVillaContactDeleteToWebSocket(
                                    villa.villaId,
                                    contact.contactId
                                ) // Bu, VillaContact silme mesajı
                                showToast("${contact.contactName} adlı kişinin Villa ${villa.villaNo} ile ilişkisi silindi.")

                                // 2. Kişinin başka ilişkisi kalıp kalmadığını kontrol et
                                val associationsCount = appDatabase.villaContactDao()
                                    .getVillaAssociationsCount(contact.contactId)
                                if (associationsCount == 0) {
                                    // 3. Başka ilişki kalmadıysa, kişiyi tamamen silmeyi öner
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("Kişiyi Sistemden Sil")
                                        .setMessage("${contact.contactName} adlı kişinin başka hiçbir villa ile ilişkisi kalmadı. Bu kişiyi sistemden tamamen silmek ister misiniz?")
                                        .setPositiveButton("Evet, Tamamen Sil") { _, _ ->
                                            activityScope.launch {
                                                try {
                                                    appDatabase.contactDao()
                                                        .delete(contact) // ContactDao'da @Delete suspend fun delete(contact: Contact) olmalı
                                                    showToast("${contact.contactName} sistemden tamamen silindi.")
                                                    sendContactDeleteToWebSocket(contact.contactId)
                                                } catch (e: Exception) {
                                                    showToast("${contact.contactName} sistemden silinirken hata oluştu: ${e.localizedMessage}")
                                                }
                                            }
                                        }
                                        .setNegativeButton("Hayır, Kişiyi Tut", null)
                                        .show()
                                }
                            } catch (e: Exception) {
                                showToast("İlişki silinirken hata oluştu: ${e.localizedMessage}")
                            }
                        }
                    }
                    .setNegativeButton("İptal", null)
                    .show()
            }
        )
        dialogBinding.recyclerViewVillaContacts.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = contactsAdapter
        }

        activityScope.launch {
            appDatabase.villaContactDao().getContactsForVilla(villa.villaId).collect { contacts ->
                contactsAdapter.submitList(contacts)
                dialogBinding.tvDialogTitle.text = "Villa ${villa.villaNo} Kişileri (${contacts.size})"
            }
        }

        dialogBinding.btnAddContactToVilla.setOnClickListener {
            showAddEditContactDialog(null, villa.villaId)
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Tamam") { dialog, _ -> dialog.dismiss() }
            .create()

        alertDialog.window?.setBackgroundDrawableResource(R.color.transparent)
        alertDialog.show()
    }

    private fun showAddEditContactDialog(contactToEdit: Contact?, villaIdForAssociation: Int?) {
        val isEditMode = contactToEdit != null

        if (!isEditMode && villaIdForAssociation != null) {
            // Yeni kişi ekleniyor ve bir villa ile ilişkilendirilecek: Seçenekleri sun
            val options = arrayOf("Mevcut Kişilerden Seç", "Yeni Kişi Oluştur")
            AlertDialog.Builder(this)
                .setTitle("Villaya Kişi Ekle")
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> { // Mevcut Kişilerden Seç
                            showExistingContactsSelectionDialog(villaIdForAssociation)
                        }
                        1 -> { // Yeni Kişi Oluştur
                            proceedWithNewContactCreationDialog(null, villaIdForAssociation)
                        }
                    }
                    // dialog.dismiss() // setCountDownTimer ile kapanıyorsa gerek yok, setItems kendi kapatır genelde
                }
                .setNegativeButton("İptal") { dialog, _ ->
                    dialog.cancel()
                }
                .show()
        } else {
            // Düzenleme modu veya villa ile ilişkilendirilmeden (bağımsız) kişi düzenleme/ekleme
            // Mevcut akışta bağımsız kişi ekleme/düzenleme UI'dan çağrılmıyor gibi,
            // ama düzenleme modu (contactToEdit != null) bu yoldan gelecek.
            proceedWithNewContactCreationDialog(contactToEdit, villaIdForAssociation, isEditMode)
        }
    }

    // Yeni yardımcı fonksiyon: Mevcut kişilerden seçmek için diyalog
    private fun showExistingContactsSelectionDialog(villaIdToAssociate: Int) {
        val contactsLiveData = appDatabase.contactDao().getAllContacts()

        // Observer'ı bir değişken olarak tanımlıyoruz
        val observer = object : Observer<List<Contact>> {
            override fun onChanged(value: List<Contact>) {
                // Veri alındıktan ve diyalog gösterilmeden hemen önce gözlemciyi kaldırıyoruz.
                // Bu, bu spesifik diyalog gösterimi için gözlemcinin sadece bir kez çalışmasını sağlar.
                contactsLiveData.removeObserver(this)

                if (value.isEmpty()) {
                    showToast("Sistemde kayıtlı kişi bulunmuyor. Önce yeni kişi oluşturun.")
                    // Eğer burada return ediyorsak ve dialog gösterilmiyorsa,
                    // kullanıcı tekrar "Mevcut kişilerden seç" dediğinde yeni bir observer eklenecektir. Bu doğru.
                    return
                }

                val contactNames = value.map { it.contactName ?: "İsimsiz" }.toTypedArray()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Mevcut Kişilerden Seç")
                    .setItems(contactNames) { dialog, which ->
                        val selectedContact = value[which]
                        activityScope.launch {
                            val defaultContactType = "Diğer"
                            val existingRelation = appDatabase.villaContactDao().getVillaContact(
                                villaIdToAssociate,
                                selectedContact.contactId,
                                defaultContactType
                            )
                            if (existingRelation != null && existingRelation.contactType == defaultContactType) {
                                showToast("${selectedContact.contactName} zaten bu villa ile '$defaultContactType' tipinde ilişkili.")
                            } else {
                                val villaContact = VillaContact(
                                    villaId = villaIdToAssociate,
                                    contactId = selectedContact.contactId,
                                    isRealOwner = 0,
                                    contactType = defaultContactType,
                                    notes = null
                                )
                                appDatabase.villaContactDao().insert(villaContact)
                                showToast("${selectedContact.contactName}, villaya '$defaultContactType' tipiyle eklendi.")
                                sendVillaContactAddToWebSocket(
                                    VillaContact(
                                        villaIdToAssociate,
                                        selectedContact.contactId,
                                        villaContact.isRealOwner,
                                        villaContact.contactType,
                                        villaContact.notes
                                    )
                                )
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("İptal") { dialog, _ ->
                        dialog.dismiss()
                    }
                    // .setOnDismissListener {
                    //     // Gözlemci zaten onChanged içinde kaldırıldığı için bu kısım
                    //     // bu spesifik senaryoda gerekmeyebilir. Ama genel bir kural olarak
                    //     // diyalog kapandığında kaynakları temizlemek iyidir.
                    //     // contactsLiveData.removeObserver(this) // 'this' burada Observer'a referans vermez.
                    // }
                    .show()
            }
        }
        // Gözlemciyi LiveData'ya ekliyoruz
        contactsLiveData.observe(this, observer)
    }

    private fun proceedWithNewContactCreationDialog(
        contactToEdit: Contact?,
        villaIdForAssociation: Int?,
        isEditModeOverride: Boolean? = null
    ) {
        val dialogBinding = DialogAddEditContactBinding.inflate(layoutInflater)
        val isEditMode = isEditModeOverride ?: (contactToEdit != null)

        if (isEditMode) {
            dialogBinding.etContactName.setText(contactToEdit?.contactName)
            dialogBinding.etContactPhone.setText(contactToEdit?.contactPhone)
        }

        val dialogTitle = if (isEditMode) "Kişi Düzenle" else "Yeni Kişi Oluştur"
        val positiveButtonText = if (isEditMode) "Güncelle" else "Ekle"

        val alertDialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(dialogBinding.root)
            .setPositiveButton(positiveButtonText, null) // Otomatik kapanmayı engellemek için null
            .setNegativeButton("İptal") { dialog, _ -> dialog.cancel() }
            .create()

        alertDialog.setOnShowListener { dialogInterface ->
            val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val contactName = dialogBinding.etContactName.text.toString().trim()
                val contactPhone = dialogBinding.etContactPhone.text.toString().trim()

                if (contactName.isBlank() || contactPhone.isBlank()) {
                    showToast("Kişi adı ve telefon numarası boş olamaz.")
                    return@setOnClickListener // Diyalog açık kalır
                }

                activityScope.launch {
                    if (isEditMode) {
                        contactToEdit?.let {
                            val updatedContact = it.copy(
                                contactName = contactName,
                                contactPhone = contactPhone
                            )
                            appDatabase.contactDao().update(updatedContact)
                            showToast("Kişi ${updatedContact.contactName} güncellendi.")
                            sendContactAddOrUpdateToWebSocket(updatedContact) // Bu fonksiyonun UPDATE için ayrı bir type göndermesi gerekebilir
                            alertDialog.dismiss()
                        }
                    } else {
                        // Yeni Kişi Oluşturma Akışı
                        val existingContact = appDatabase.contactDao().getContactByNameAndPhone(contactName, contactPhone)
                        if (existingContact != null) {
                            // Kişi mevcut, kullanıcıya sor
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Kişi Mevcut")
                                .setMessage("'$contactName' adlı ve '$contactPhone' numaralı kişi zaten sistemde kayıtlı. Bu kişiyi mi kullanmak istersiniz?")
                                .setPositiveButton("Evet, Kullan") { _, _ ->
                                    villaIdForAssociation?.let { villaId ->
                                        linkContactToVilla(existingContact, villaId)
                                    } ?: showToast("Villa ID bulunamadı, kişi villaya bağlanamadı.")
                                    alertDialog.dismiss() // Ana diyalogu kapat
                                }
                                .setNegativeButton("Hayır, Yeni Oluştur") { _, _ ->
                                    // Kullanıcı yine de yeni oluşturmak istiyorsa (belki farklı bir not ile vs.)
                                    // Bu senaryo genellikle aynı isim/telefonla ikinci bir kayıt oluşturmamak üzerine kurulur.
                                    // Eğer aynı bilgilerle yeni bir contactId ile eklemeye izin vereceksek,
                                    // aşağıdaki `createNewContactAndLink` kısmı çalışır.
                                    // Genelde "Hayır" seçeneği, kullanıcının girdiği bilgileri değiştirmesi için
                                    // ana diyalogda kalmasını sağlamalı. Şimdilik "Hayır" da yeniymiş gibi devam ettiriyor (hatalı olabilir).
                                    // Daha iyi bir UX: "Hayır" denildiğinde, ana diyalogda kalıp bilgileri düzenlemesine izin ver.
                                    // Şimdilik, "Hayır" seçeneği de sanki kişi yokmuş gibi devam edecek (aşağıdaki kod).
                                    // Bu, mükerrer kayda yol açabilir. Bu kısmı daha sonra iyileştirmek gerekebilir.
                                    // Şimdilik, "Hayır" = "İptal Et ve Geri Dön" gibi davranmasını sağlayabiliriz.
                                    // Ya da "Hayır, farklı bir kişi ekleyeceğim" diyerek kullanıcıyı bilgilere geri döndürmeli.
                                    // En basiti:
                                    showToast("Lütfen kişi bilgilerini değiştirin veya işlemi iptal edin.")
                                    // alertDialog (ana dialog) açık kalır, kullanıcı bilgileri değiştirebilir.
                                    // Ya da "Hayır" butonu direk dialogu kapatabilir ve kullanıcı tekrar "Yeni Kişi Oluştur" demeli.
                                    // Şimdilik "Hayır"ın bir şey yapmamasını ve ana diyalogun açık kalmasını sağlıyorum.
                                }
                                .setNeutralButton("İptal") { confDialog, _ -> confDialog.dismiss()}
                                .show()
                        } else {
                            // Kişi mevcut değil, yeni oluştur ve villaya bağla
                            val newContact =
                                Contact(contactName = contactName, contactPhone = contactPhone)
                            val insertedContactId = appDatabase.contactDao().insert(newContact) // Long döner
                            if (insertedContactId > 0) {
                                val createdContact = newContact.copy(contactId = insertedContactId.toInt())
                                showToast("Yeni kişi ${createdContact.contactName} oluşturuldu.")
                                sendContactAddOrUpdateToWebSocket(createdContact) // ADD type ile gitmeli

                                villaIdForAssociation?.let { villaId ->
                                    linkContactToVilla(createdContact, villaId)
                                }
                                alertDialog.dismiss()
                            } else {
                                showToast("Yeni kişi oluşturulurken veritabanı hatası.")
                            }
                        }
                    }
                }
            }
        }
        alertDialog.show()
    }

    // Yeni yardımcı fonksiyon: Kişiyi villaya bağlamak için
    private fun linkContactToVilla(contact: Contact, villaId: Int) {
        activityScope.launch {
            // Villa-Kişi ilişkisi zaten var mı diye son bir kontrol (genelde showExistingContactsSelectionDialog'da yapıldı ama burada da olabilir)
            val existingRelation = appDatabase.villaContactDao().getVillaContact(villaId, contact.contactId, "Diğer")
            if (existingRelation != null) {
                showToast("${contact.contactName} zaten Villa $villaId ile ilişkili.")
            } else {
                // TODO: VillaContact için diğer detayları (isRealOwner, contactType, notes) kullanıcıdan almak üzere
                //       ayrı bir diyalog açılabilir veya varsayılan değerler kullanılabilir.
                //       Şimdilik varsayılan/null değerler kullanılıyor.
                val villaContact = VillaContact(
                    villaId = villaId,
                    contactId = contact.contactId,
                    isRealOwner = 0, // Varsayılan
                    contactType = "Diğer", // Varsayılan
                    notes = null
                )
                appDatabase.villaContactDao().insert(villaContact)
                showToast("${contact.contactName} başarıyla Villa $villaId için eklendi.")
                sendVillaContactAddToWebSocket(
                    VillaContact(
                        villaId,
                        contact.contactId,
                        villaContact.isRealOwner,
                        villaContact.contactType,
                        villaContact.notes
                    )
                )
            }
        }
    }


// sendContactAddOrUpdateToWebSocket ve sendVillaContactAddToWebSocket fonksiyonlarınızın
// sunucu tarafındaki DTO'larla ve mesaj tipleriyle uyumlu olduğundan emin olun.
// Örneğin, sendContactAddOrUpdateToWebSocket içindeki WebSocketMessage'ın "type" alanı
// ekleme ("add_contact") ve güncelleme ("update_contact") için farklı olmalı.
// Bu fonksiyonlar şu anki tanımlarına göre bu ayrımı yapmıyor olabilir.

    //region WebSocket Gönderim Fonksiyonları
    private fun sendVillaAddOrUpdateToWebSocket(villa: Villa) {
        val type = if (villa.villaId > 0) "update_villa" else "add_villa"
        val villaDto = VillaDto(
            villaId = if (villa.villaId > 0) villa.villaId else null,
            villaNo = villa.villaNo,
            villaNotes = villa.villaNotes.takeIf { !it.isNullOrBlank() },
            villaStreet = villa.villaStreet.takeIf { !it.isNullOrBlank() },
            villaNavigation = villa.villaNavigation.takeIf { !it.isNullOrBlank() },
            isVillaUnderConstruction = villa.isVillaUnderConstruction,
            isVillaSpecial = villa.isVillaSpecial,
            isVillaRental = villa.isVillaRental,
            isVillaCallFromHome = villa.isVillaCallFromHome,
            isVillaCallForCargo = villa.isVillaCallForCargo,
            isVillaEmpty = villa.isVillaEmpty
        )
        val message = gson.toJson(WebSocketMessage(type = type, data = villaDto))
        webSocketClient.sendMessage(message)
    }

    private fun sendContactAddOrUpdateToWebSocket(contact: Contact) {
        val type = if (contact.contactId > 0) "update_contact" else "add_contact"
        val contactDto = ContactDto(
            contactId = if (contact.contactId > 0) contact.contactId else null,
            contactName = contact.contactName,
            contactPhone = contact.contactPhone
        )
        val message = gson.toJson(WebSocketMessage(type = type, data = contactDto))
        webSocketClient.sendMessage(message)
    }

    private fun sendContactDeleteToWebSocket(contactId: Int) {
        if (!webSocketClient.isConnected()) {
            showToast("WebSocket bağlantısı yok. Silme işlemi sunucuya iletilemedi.")
            return
        }
        val contactDeleteDto =
            ContactDto(contactId = contactId, contactName = null, contactPhone = null)
        val webSocketMessage = WebSocketMessage(
            type = "delete_contact",
            data = gson.toJson(contactDeleteDto) // DTO'yu JSON string'ine çeviriyoruz
        )
        // WebSocketMessage nesnesinin tamamını JSON string'ine çeviriyoruz
        val messageToSend = gson.toJson(webSocketMessage)
        webSocketClient.sendMessage(messageToSend)
        // showToast("Kişi silme bilgisi sunucuya gönderildi: ID $contactId") // İsteğe bağlı: Başarılı gönderim mesajı
    }

    private fun sendVillaContactAddToWebSocket(villaContact: VillaContact) {
        val vcDto = VillaContactDto(
            villaId = villaContact.villaId,
            contactId = villaContact.contactId,
            isRealOwner = villaContact.isRealOwner,
            contactType = villaContact.contactType,
            notes = villaContact.notes
        )
        val message = gson.toJson(WebSocketMessage(type = "add_villacontact", data = vcDto))
        webSocketClient.sendMessage(message)
    }

    private fun sendVillaContactDeleteToWebSocket(villaId: Int, contactId: Int) {
        val deleteDto = VillaContactDeleteDto(villaId = villaId, contactId = contactId)
        val message = gson.toJson(WebSocketMessage(type = "delete_villacontact", data = deleteDto))
        webSocketClient.sendMessage(message)
    }
    //endregion

    override fun onStart() {
        super.onStart()
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
                    showToast("Sunucudan gelen mesaj: $message")
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
        binding.textMessage.text = message
        binding.blurMessage.visibility = View.VISIBLE
        binding.blurMessage.postDelayed({
            binding.blurMessage.visibility = View.GONE
        }, 7000)
        binding.btnClear.setOnClickListener {
            binding.blurMessage.visibility = View.GONE
        }
    }
}