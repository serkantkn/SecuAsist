package com.serkantken.secuasist.views.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.R
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.adapters.AvailableVillasAdapter
import com.serkantken.secuasist.adapters.SelectedVillasAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.ActivityChooseCargoBinding
import com.serkantken.secuasist.models.Cargo
import com.serkantken.secuasist.models.SelectableVilla
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.utils.Tools
import kotlinx.coroutines.launch
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChooseCargoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChooseCargoBinding
    private lateinit var appDatabase: AppDatabase
    val allVillas = mutableListOf<SelectableVilla>()
    val selectedVillas = mutableListOf<SelectableVilla>()
    lateinit var availableVillasAdapter: AvailableVillasAdapter
    lateinit var selectedVillasAdapter: SelectedVillasAdapter
    private var companyId = 0
    private var systemBarsTop: Int = 0
    private var systemBarsBottom: Int = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseCargoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        Hawk.init(this).build()
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBarsTop = systemBars.top
            systemBarsBottom = systemBars.bottom
            v.setPadding(
                Tools(this@ChooseCargoActivity).convertDpToPixel(16),
                systemBars.top,
                Tools(this@ChooseCargoActivity).convertDpToPixel(16),
                0)
            binding.rvAvailableVillas.setPadding(
                Tools(this@ChooseCargoActivity).convertDpToPixel(12),
                systemBars.top + Tools(this@ChooseCargoActivity).convertDpToPixel(100),
                Tools(this@ChooseCargoActivity).convertDpToPixel(12),
                systemBars.bottom + Tools(this@ChooseCargoActivity).convertDpToPixel(25))
            insets
        }
        //if (Hawk.contains("less_blur"))
        Tools(this@ChooseCargoActivity).blur(arrayOf(binding.blurBtnCreateCargos, binding.blurBtnClose, binding.blurSearchbox, binding.selectedItemsLayout), 15f, true)
        appDatabase = AppDatabase.getDatabase(this)
        companyId = intent.getIntExtra("COMPANY_ID", 0)

        availableVillasAdapter = AvailableVillasAdapter(this) { selectableVilla ->
            moveVilla(selectableVilla, from = allVillas, to = selectedVillas)
        }
        binding.rvAvailableVillas.apply {
            adapter = availableVillasAdapter
            clipToPadding = false
        }

        selectedVillasAdapter = SelectedVillasAdapter { selectableVilla ->
            moveVilla(selectableVilla, from = selectedVillas, to = allVillas)
        }
        binding.rvSelectedVillas.adapter = selectedVillasAdapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateAdapters()
            }
        })

        lifecycleScope.launch {
            try {
                val villasFromDb: List<Villa> = appDatabase.villaDao().getAllVillasAsList()
                val selectableVillasList = villasFromDb.map { villa ->
                    val defaultContact = appDatabase.villaContactDao().getRealOwnersForVillaNonFlow(villa.villaId).firstOrNull()
                        ?: appDatabase.villaContactDao().getContactsForVillaNonFlow(villa.villaId).firstOrNull()
                    SelectableVilla(villa, defaultContact?.contactId, defaultContact?.contactName)
                }
                allVillas.clear()
                allVillas.addAll(selectableVillasList)
                selectedVillas.clear()
                updateAdapters()
            } catch (e: Exception) {
                Log.e("SelectRecipientsDialog", "Veri yükleme hatası", e)
            }
        }

        binding.btnClose.setOnClickListener { finish() }
        binding.btnCreateCargos.isEnabled = false
        binding.btnCreateCargos.setOnClickListener {
            if (selectedVillas.isEmpty()) return@setOnClickListener

            lifecycleScope.launch {
                val newCargoIds = mutableListOf<Int>()
                selectedVillas.forEach { selectableVilla ->
                    val cargo = Cargo(
                        companyId = companyId,
                        villaId = selectableVilla.villa.villaId,
                        whoCalled = selectableVilla.defaultContactId,
                        isCalled = 0, isMissed = 0,
                        date = SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss'Z'",
                            Locale.getDefault()
                        ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date()),
                        callDate = null, callAttemptCount = 0
                    )
                    try {
                        val insertedId = appDatabase.cargoDao().insert(cargo)
                        val newCargoToSend = cargo.copy(cargoId = insertedId.toInt())
                        (application as? SecuAsistApplication)?.sendUpsert(newCargoToSend)
                        newCargoIds.add(insertedId.toInt())
                    } catch (e: Exception) {
                        Log.e("SelectRecipientsDialog", "Kargo ekleme hatası", e)
                    }
                }

                if (newCargoIds.isNotEmpty()) { // newCargoIds sadece yeni eklenenlerin ID'lerini tutuyor
                    // Yeni kargolar eklendi, şimdi TÜM bekleyen kargoları çekelim
                    // companyId'nin bu scope'ta erişilebilir olduğundan emin olun.
                    // Bu, showSelectRecipientsDialog metodunun bir parametresi olmalı.
                    val allUncalledCargosForCompany = appDatabase.cargoDao().getUncalledCargosForCompanyAsList(companyId)

                    if (allUncalledCargosForCompany.isNotEmpty()) {
                        val intent = Intent(this@ChooseCargoActivity, CallingActivity::class.java)
                        // CallingActivity'ye TÜM bekleyen kargoları gönder
                        intent.putExtra("CARGO_LIST", ArrayList(allUncalledCargosForCompany) as Serializable)
                        startActivity(intent)
                        finish()
                    } else {
                        // Bu durum pek olası değil çünkü en azından yeni eklenenler olmalı
                        // Eğer buraya düşerse, triggerRefresh() çağrılabilir.
                        Toast.makeText(this@ChooseCargoActivity, "Bu şirkete ait gösterilecek kargo bulunamadı.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this@ChooseCargoActivity, "Kargo oluşturulamadı.", Toast.LENGTH_SHORT).show()
                    // Belki burada da triggerRefresh() çağrılabilir.
                }
            }
        }
    }

    fun moveVilla(villaToMove: SelectableVilla, from: MutableList<SelectableVilla>, to: MutableList<SelectableVilla>) {
        if (from.removeIf { it.villa.villaId == villaToMove.villa.villaId }) {
            to.add(villaToMove)
            updateAdapters()
        }
    }

    fun updateAdapters() {
        val query = binding.etSearch.text.toString()
        val filteredList = if (query.isBlank()) {
            allVillas.toList()
        } else {
            val lowerCaseQuery = query.lowercase()
            allVillas.filter {
                it.villa.villaNo.toString().contains(lowerCaseQuery) ||
                        it.defaultContactName?.lowercase()?.contains(lowerCaseQuery) == true
            }
        }
        availableVillasAdapter.differ.submitList(filteredList.sortedBy { it.villa.villaNo })
        binding.rvAvailableVillas.smoothScrollToPosition(0)
        selectedVillasAdapter.differ.submitList(selectedVillas.sortedBy { it.villa.villaNo }.toList())
        binding.btnCreateCargos.isEnabled = selectedVillas.isNotEmpty()
        if (selectedVillas.isEmpty()) {
            binding.selectedItemsLayout.visibility = View.GONE
            binding.rvAvailableVillas.setPadding(
                Tools(this@ChooseCargoActivity).convertDpToPixel(12),
                systemBarsTop + Tools(this@ChooseCargoActivity).convertDpToPixel(100),
                Tools(this@ChooseCargoActivity).convertDpToPixel(12),
                systemBarsBottom + Tools(this@ChooseCargoActivity).convertDpToPixel(25)
            )
        } else {
            binding.selectedItemsLayout.visibility = View.VISIBLE
            binding.rvAvailableVillas.setPadding(
                Tools(this@ChooseCargoActivity).convertDpToPixel(12),
                systemBarsTop + Tools(this@ChooseCargoActivity).convertDpToPixel(225),
                Tools(this@ChooseCargoActivity).convertDpToPixel(12),
                systemBarsBottom + Tools(this@ChooseCargoActivity).convertDpToPixel(25)
            )
        }
    }
}