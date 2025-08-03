package com.serkantken.secuasist.views.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.serkantken.secuasist.R
import com.serkantken.secuasist.adapters.CallingVillaAdapter
import com.serkantken.secuasist.adapters.ContactsAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.ActivityCallingBinding
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.DisplayableVilla
import com.serkantken.secuasist.models.VillaCallingState
import com.serkantken.secuasist.views.fragments.SelectCargoRecipientsSheet
import kotlinx.coroutines.launch

class CallingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallingBinding
    private lateinit var db: AppDatabase
    private lateinit var callingVillaAdapter: CallingVillaAdapter
    private lateinit var contactsAdapter: ContactsAdapter // Alttaki kişiler için

    private var displayableVillas = mutableListOf<DisplayableVilla>()
    private var cargoIds: ArrayList<Int>? = null
    private var currentInProgressVillaId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = AppDatabase.getDatabase(this)
        cargoIds = intent.getIntegerArrayListExtra(SelectCargoRecipientsSheet.EXTRA_CARGO_IDS)

        if (cargoIds.isNullOrEmpty()) {
            // Hata yönetimi, kargo ID'leri olmadan bu aktivite çalışamaz
            finish()
            return
        }

        setupVillaRecyclerView()
        setupContactsRecyclerView() // Kişi RecyclerView'ı için de bir setup metodu

        loadInitialData()
    }

    private fun setupVillaRecyclerView() {
        callingVillaAdapter = CallingVillaAdapter { clickedVilla ->
            handleVillaClick(clickedVilla)
        }
        binding.rvVillaList.apply { // XML'deki RecyclerView ID'niz (rvVillaCarousel veya rvVillasList)
            layoutManager =
                LinearLayoutManager(this@CallingActivity,
                    LinearLayoutManager.VERTICAL, false)
            adapter = callingVillaAdapter
            // Ortalamayı daha pürüzsüz yapmak için item animator'u kapatmayı düşünebilirsiniz,
            // ya da özel animasyonlar için açık bırakabilirsiniz.
            // itemAnimator = null
        }
    }
    private fun setupContactsRecyclerView() {
        contactsAdapter = ContactsAdapter(
            onItemClick = { contact, _ -> // VillaContact? parametresini burada kullanmayacağımız için _ ile geçiştiriyoruz
                // TODO: Kişiye tıklama olayını işle.
                // Örneğin, bu kişiyi mevcut IN_PROGRESS villası için ara.
                // val phoneNumber = contact.contactPhone
                // directCall(phoneNumber) // Veya bir arama intent'i başlat
                // Log.d("CallingActivity", "Contact clicked: ${contact.contactName}, Phone: ${contact.contactPhone}")
                // Gerekirse, hangi villayla ilişkili olduğunu currentInProgressVillaId üzerinden bilebiliriz.
            },
            onDeleteClick = { contact, _ ->
                // CallingActivity'deki bu kişi listesinde silme işlemi muhtemelen uygulanmayacak.
                // Log.d("CallingActivity", "Delete clicked for contact ${contact.contactName}, but not handled in this context.")
            }
        )
        binding.rvContacts.apply { // XML'deki kişi RecyclerView ID'niz
            layoutManager = LinearLayoutManager(this@CallingActivity) // Dikey veya yatay olabilir
            adapter = contactsAdapter
        }
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            if (cargoIds.isNullOrEmpty()) return@launch

            val villasToShow = mutableListOf<DisplayableVilla>()
            // İlk kargonun villasını IN_PROGRESS yap, diğerlerini PENDING
            cargoIds!!.forEachIndexed { index, cargoId ->
                val cargo = db.cargoDao().getCargoById(cargoId) ?: return@forEachIndexed
                val villa = db.villaDao().getVillaById(cargo.villaId) ?: return@forEachIndexed

                // Sahip/Aranacak kişiyi bulma mantığı:
                // Önce cargo.whoCalled (ContactId) ile kişiyi almayı dene.
                // Yoksa villanın gerçek sahiplerini al.
                // O da yoksa villanın herhangi bir ilişkili kişisini al.
                var ownerContact: Contact? = cargo.whoCalled?.let { db.contactDao().getContactById(it) }
                if (ownerContact == null) {
                    ownerContact = db.villaContactDao().getRealOwnersForVillaNonFlow(villa.villaId).firstOrNull()
                }
                if (ownerContact == null) {
                    ownerContact = db.villaContactDao().getContactsForVillaNonFlow(villa.villaId).firstOrNull()
                }

                val state = if (index == 0) VillaCallingState.IN_PROGRESS else VillaCallingState.PENDING
                if (state == VillaCallingState.IN_PROGRESS) {
                    currentInProgressVillaId = villa.villaId
                }
                villasToShow.add(DisplayableVilla(villa, ownerContact?.contactName, state))
            }

            displayableVillas.clear()
            displayableVillas.addAll(villasToShow)
            callingVillaAdapter.submitList(displayableVillas.toList()) // Adaptöre yeni listeyi ver

            // IN_PROGRESS olan villayı ortaya kaydır ve kişilerini yükle
            val inProgressIndex = displayableVillas.indexOfFirst { it.state == VillaCallingState.IN_PROGRESS }
            if (inProgressIndex != -1) {
                scrollToCenter(inProgressIndex)
                loadContactsForVilla(displayableVillas[inProgressIndex].villa.villaId)
            }
        }
    }

    private fun handleVillaClick(clickedVillaDto: DisplayableVilla) {
        val clickedVillaId = clickedVillaDto.villa.villaId
        // Eğer zaten IN_PROGRESS olan villaya tıklandıysa bir şey yapma
        if (currentInProgressVillaId == clickedVillaId) return

        val newVillasList = displayableVillas.map { villa ->
            when (villa.villa.villaId) {
                clickedVillaId -> villa.copy(state = VillaCallingState.IN_PROGRESS)
                currentInProgressVillaId -> villa.copy(state = VillaCallingState.PENDING) // Eski IN_PROGRESS PENDING oldu
                else -> villa // Diğerleri durumunu korur
            }
        }

        currentInProgressVillaId = clickedVillaId
        displayableVillas.clear()
        displayableVillas.addAll(newVillasList)
        callingVillaAdapter.submitList(displayableVillas.toList())

        val newInProgressIndex = displayableVillas.indexOfFirst { it.state == VillaCallingState.IN_PROGRESS }
        if (newInProgressIndex != -1) {
            scrollToCenter(newInProgressIndex)
            loadContactsForVilla(displayableVillas[newInProgressIndex].villa.villaId)
            // TODO: Arama butonlarının durumunu/işlevini güncelle
        }
    }

    private fun scrollToCenter(position: Int) {
        val layoutManager = binding.rvVillaList.layoutManager as LinearLayoutManager
        val recyclerViewHeight = binding.rvVillaList.height
        // Öğenin yüksekliğini almamız gerekiyor, bu her zaman bind sırasında mevcut olmayabilir.
        // Ortalama bir yükseklik veya ilk öğenin yüksekliği kullanılabilir.
        // Daha kesin bir ortalama için, viewholder'dan yüksekliği almak ve callback ile bildirmek gerekir.
        // Şimdilik, basit bir offset ile pozisyona kaydıralım.
        // Tam ortalama için: val offset = recyclerViewHeight / 2 - (view?.height ?: 0) / 2
        // layoutManager.scrollToPositionWithOffset(position, offset)

        // Daha basit ve genellikle işe yarayan bir yaklaşım:
        layoutManager.scrollToPositionWithOffset(position, recyclerViewHeight / 3) // Ekranın yaklaşık üçte birine kaydırır
        // Veya sadece scrollToPosition(position) da kullanabilirsiniz
        // eğer eleman tam ortaya gelmese de listenin o kısmına gelmesi yeterliyse.
    }

    private fun loadContactsForVilla(villaId: Int) {
        lifecycleScope.launch {
            val contacts = db.villaContactDao().getContactsForVillaNonFlow(villaId)
            contactsAdapter.submitList(contacts)
            // TODO: Eğer kişi yoksa "kişi bulunamadı" mesajı göster
        }
    }

    // TODO: btnCall, btnCallFromHome, btnSkip için tıklama olaylarını ve işlevlerini ekle
    // Bu butonlar currentInProgressVillaId'yi ve onun seçili kişisini (eğer varsa) kullanmalı.
}