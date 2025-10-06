package com.serkantken.secuasist.views.fragments

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.serkantken.secuasist.R
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.adapters.AvailableVillasAdapter
import com.serkantken.secuasist.adapters.CargoCompanyAdapter
import com.serkantken.secuasist.adapters.SelectedVillasAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.DialogSelectRecipientsBinding
import com.serkantken.secuasist.databinding.FragmentCargoBinding
import com.serkantken.secuasist.models.Cargo
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.DisplayCargoCompany
import com.serkantken.secuasist.models.SelectableVilla
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.utils.Tools
import com.serkantken.secuasist.views.activities.CallingActivity
import com.serkantken.secuasist.views.activities.MainActivity
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.createBalloon
import com.skydoves.balloon.overlay.BalloonOverlayRoundRect
import eightbitlab.com.blurview.BlurView
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@ExperimentalBadgeUtils
class CargoFragment : Fragment(), CargoCompanyAdapter.OnCargoCompanyActionListener {

    private var _binding: FragmentCargoBinding? = null
    private val binding get() = _binding!!

    private lateinit var cargoCompanyAdapter: CargoCompanyAdapter
    private lateinit var appDatabase: AppDatabase
    private val searchQuery = MutableStateFlow<String?>(null)
    private val refreshTrigger = MutableStateFlow(UUID.randomUUID().toString())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCargoBinding.inflate(inflater, container, false)
        appDatabase = AppDatabase.getDatabase(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rvCargoCompanies) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                Tools(requireActivity()).convertDpToPixel(16),
                systemBars.top + Tools(requireActivity()).convertDpToPixel(55),
                Tools(requireActivity()).convertDpToPixel(16),
                systemBars.bottom + Tools(requireActivity()).convertDpToPixel(72)
            )
            insets
        }

        setupRecyclerView()
        setupSwipeRefreshLayout()
        observeCargoCompanies()
    }

    override fun onResume() {
        super.onResume()
        Log.d("CargoFragment", "onResume called, triggering refresh.")
        // Fragment tekrar görünür olduğunda verileri yenile ve animasyonu başlat
        if (_binding != null) { // binding'in null olmadığından emin ol
            binding.swipeRefreshLayout.isRefreshing = true
        }
        triggerRefresh()
    }

    private fun setupRecyclerView() {
        cargoCompanyAdapter = CargoCompanyAdapter(
            onItemLongClick = { company: CargoCompany ->
                (activity as? MainActivity)?.showAddEditCargoCompanyDialog(company)
            }
        )
        cargoCompanyAdapter.setOnCargoCompanyActionListener(this)

        binding.rvCargoCompanies.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = cargoCompanyAdapter
        }
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d("CargoFragment", "Swipe to refresh triggered.")
            // SwipeRefreshLayout zaten kendi animasyonunu isRefreshing = true yaparak başlatır.
            // Biz sadece veri akışını tetiklemeliyiz.
            // Ancak, onResume'dan farklı olarak, kullanıcı başlattığı için isRefreshing'i burada da true yapmak
            // (eğer zaten değilse) tutarlılık sağlayabilir. Genellikle listener tetiklendiğinde zaten true olur.
            binding.swipeRefreshLayout.isRefreshing = true // Animasyonu başlat (genelde zaten başlar)
            triggerRefresh()
        }
        // İsteğe bağlı: Renk şeması
        // binding.swipeRefreshLayout.setColorSchemeResources(R.color.your_color_primary, R.color.your_color_accent)
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    private fun triggerRefresh() {
        Log.d("CargoFragment", "triggerRefresh: Emitting new value to refreshTrigger.")
        refreshTrigger.value = UUID.randomUUID().toString()
    }

    private fun observeCargoCompanies() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                appDatabase.cargoCompanyDao().getAllCargoCompanies(),
                searchQuery.debounce(300L), // Arama için debounce (milisaniye)
                refreshTrigger
            ) { allCompanies, query, refreshSignal -> // refreshSignal'ın değeri önemli değil
                Log.d("CargoFragment", "Combine triggered. Query: '$query', RefreshSignal: '$refreshSignal'")
                // `isRefreshing` burada `true` yapılmamalı, çünkü bu blok sıkça tetiklenebilir (örn: arama sırasında).
                // Animasyon başlangıcı onResume ve onRefreshListener'da yönetiliyor.
                if (query.isNullOrBlank()) {
                    allCompanies
                } else {
                    allCompanies.filter { company ->
                        company.companyName?.contains(query, ignoreCase = true) ?: false
                    }
                }
            }
                .mapLatest { filteredCompanies ->
                    Log.d("CargoFragment", "Mapping ${filteredCompanies.size} companies to DisplayCargoCompany.")
                    filteredCompanies.map { company ->
                        async(viewLifecycleOwner.lifecycleScope.coroutineContext) { // Daha spesifik context
                            val hasUncalled = appDatabase.cargoDao().hasUncalledCargosForCompany(company.companyId)
                            DisplayCargoCompany(
                                company = company,
                                hasUncalledCargos = hasUncalled
                            )
                        }
                    }.awaitAll()
                }
                .collectLatest { displayCompanies ->
                    Log.d("CargoFragment", "Submitting ${displayCompanies.size} companies to adapter.")
                    cargoCompanyAdapter.submitList(displayCompanies.sortedBy { it.company.companyName }) // Ada göre sıralama
                    if (_binding != null) { // binding'in null olmadığından emin ol
                        binding.swipeRefreshLayout.isRefreshing = false // Veri yüklendikten sonra animasyonu durdur
                    }
                    Log.d("CargoFragment", "Refresh animation stopped.")
                }
        }
    }

    override fun onShowActionsClicked(anchorView: View, company: CargoCompany, hasUncalled: Boolean) {
        if (hasUncalled) {
            val balloon = createBalloon(requireContext()) {
                setArrowSize(0)
                setWidth(BalloonSizeSpec.WRAP)
                setHeight(BalloonSizeSpec.WRAP)
                setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
                setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                setLayout(R.layout.layout_balloon_has_uncalled_cargos)
                setIsVisibleOverlay(true)
                overlayColor = ContextCompat.getColor(requireContext(), R.color.black_transparent)
                setOverlayShape(BalloonOverlayRoundRect(100f, 100f))
                setLifecycleOwner(viewLifecycleOwner)
                build()
            }

            val blur: BlurView = balloon.getContentView().findViewById(R.id.layout_menu_balloon)
            val btnContinueCalling: ConstraintLayout = balloon.getContentView().findViewById(R.id.btn_resume_calling)
            val btnAddNewCargos: ConstraintLayout = balloon.getContentView().findViewById(R.id.btn_add_new_cargos)
            Tools(requireActivity()).blur(arrayOf(blur), 10f, true)

            btnContinueCalling.setOnClickListener {
                lifecycleScope.launch {
                    val uncalledCargos = appDatabase.cargoDao().getUncalledCargosForCompanyAsList(company.companyId)
                    if (uncalledCargos.isNotEmpty()) {
                        val intent = Intent(requireActivity(), CallingActivity::class.java).apply {
                            putExtra("CARGO_LIST", ArrayList(uncalledCargos) as ArrayList<Serializable>)
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(requireContext(), "Bu şirkete ait aranacak kargo bulunamadı.", Toast.LENGTH_SHORT).show()
                    }
                }
                balloon.dismiss()
            }

            btnAddNewCargos.setOnClickListener {
                balloon.dismiss()
                showSelectRecipientsDialog(company.companyId)
            }

            balloon.showAlignBottom(anchorView)
        } else {
            showSelectRecipientsDialog(company.companyId)
        }
    }

    private fun showSelectRecipientsDialog(companyId: Int) {
        // ... (Bu metodun içeriği öncekiyle aynı, değişiklik yok) ...
        val dialogBinding = DialogSelectRecipientsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        Tools(requireActivity()).blur(arrayOf(dialogBinding.blurWindow, dialogBinding.selectedItemsLayout), 15f, true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val allVillas = mutableListOf<SelectableVilla>()
        val selectedVillas = mutableListOf<SelectableVilla>()

        lateinit var availableVillasAdapter: AvailableVillasAdapter
        lateinit var selectedVillasAdapter: SelectedVillasAdapter

        fun updateAdapters() {
            val query = dialogBinding.etSearch.text.toString()
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
            selectedVillasAdapter.differ.submitList(selectedVillas.sortedBy { it.villa.villaNo }.toList())
            dialogBinding.btnCreateCargos.isEnabled = selectedVillas.isNotEmpty()
            dialogBinding.selectedItemsLayout.visibility = if (selectedVillas.isEmpty()) View.GONE else View.VISIBLE
        }

        fun moveVilla(villaToMove: SelectableVilla, from: MutableList<SelectableVilla>, to: MutableList<SelectableVilla>) {
            if (from.removeIf { it.villa.villaId == villaToMove.villa.villaId }) {
                to.add(villaToMove)
                updateAdapters()
            }
        }

        availableVillasAdapter = AvailableVillasAdapter(requireActivity()) { selectableVilla ->
            moveVilla(selectableVilla, from = allVillas, to = selectedVillas)
        }
        dialogBinding.rvAvailableVillas.adapter = availableVillasAdapter

        selectedVillasAdapter = SelectedVillasAdapter { selectableVilla ->
            moveVilla(selectableVilla, from = selectedVillas, to = allVillas)
        }
        dialogBinding.rvSelectedVillas.adapter = selectedVillasAdapter

        dialogBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateAdapters()
            }
            override fun afterTextChanged(s: Editable?) {}
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

        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnCreateCargos.isEnabled = false
        dialogBinding.btnCreateCargos.setOnClickListener {
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
                        (activity?.application as? SecuAsistApplication)?.sendUpsert(newCargoToSend)
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
                        val intent = Intent(activity, CallingActivity::class.java)
                        // CallingActivity'ye TÜM bekleyen kargoları gönder
                        intent.putExtra("CARGO_LIST", ArrayList(allUncalledCargosForCompany) as Serializable)
                        startActivity(intent)
                        dialog.dismiss()
                    } else {
                        // Bu durum pek olası değil çünkü en azından yeni eklenenler olmalı
                        // Eğer buraya düşerse, triggerRefresh() çağrılabilir.
                        Toast.makeText(requireContext(), "Bu şirkete ait gösterilecek kargo bulunamadı.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss() // Yine de dialogu kapat
                        triggerRefresh() // Kargo listesini yenile
                    }
                } else {
                    Toast.makeText(requireContext(), "Kargo oluşturulamadı.", Toast.LENGTH_SHORT).show()
                    // Belki burada da triggerRefresh() çağrılabilir.
                }
            }
        }
        dialog.show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Bellek sızıntılarını önlemek için
    }
}