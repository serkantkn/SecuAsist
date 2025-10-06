package com.serkantken.secuasist.views.activities

import CustomDatePickerFragment
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.serkantken.secuasist.R
import com.serkantken.secuasist.adapters.CargoReportAdapter
import com.serkantken.secuasist.adapters.SearchablePickerAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.ActivityCargoReportBinding
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.SearchableItem
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.utils.Tools
import com.serkantken.secuasist.views.fragments.SearchablePickerDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class CargoReportActivity : AppCompatActivity(), SearchablePickerDialogFragment.OnItemSelectedListener {

    private lateinit var binding: ActivityCargoReportBinding
    private lateinit var db: AppDatabase
    private lateinit var reportAdapter: CargoReportAdapter

    // Filtreleme durumu için değişkenler
    private var currentFilterMode = CargoReportAdapter.ReportMode.BY_COMPANY
    private var startDate: Calendar = Calendar.getInstance()
    private var endDate: Calendar = Calendar.getInstance()
    private var selectedCargoCompany: CargoCompany? = null
    private var selectedVilla: Villa? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCargoReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        window.isNavigationBarContrastEnforced = false
        window.navigationBarColor = getColor(android.R.color.transparent)
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top + Tools(this).convertDpToPixel(5), 0, 0)
            insets
        }
        db = AppDatabase.getDatabase(this)

        binding.chipFilterByCompany.isChecked = true
        setupInitialDates()
        setupRecyclerView()
        setupListeners()
    }

    private fun setupPickers() {
        if (binding.chipFilterByCompany.isChecked) {
            lifecycleScope.launch {
                val companies = db.cargoCompanyDao().getAllCompaniesAsList() // Bu metodun List<CargoCompany> döndürdüğünü varsayıyorum
                if (companies.isNotEmpty()) {
                    val dialogFragment = SearchablePickerDialogFragment.newInstance(
                        "Kargo Şirketi Seçin",
                        companies, // Veritabanından gelen şirket listesi
                        SearchablePickerAdapter.ItemLayoutType.COMPANY
                    )
                    dialogFragment.setOnItemSelectedListener(this@CargoReportActivity)
                    dialogFragment.show(supportFragmentManager, "CargoCompanyPicker")
                } else {
                    // Şirket bulunamadıysa kullanıcıya bilgi verilebilir
                    // Örneğin: Toast.makeText(this@CargoReportActivity, "Kayıtlı şirket bulunamadı.", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (binding.chipFilterByVilla.isChecked) {
            lifecycleScope.launch {
                val villas = db.villaDao().getAllVillasAsList() // Bu metodun List<Villa> döndürdüğünü varsayıyorum
                if (villas.isNotEmpty()) {
                    val dialogFragment = SearchablePickerDialogFragment.newInstance(
                        "Villa Seçin",
                        villas, // Veritabanından gelen villa listesi
                        SearchablePickerAdapter.ItemLayoutType.SELECTED_VILLA
                    )
                    dialogFragment.setOnItemSelectedListener(this@CargoReportActivity)
                    dialogFragment.show(supportFragmentManager, "VillaPicker")
                } else {
                    // Villa bulunamadıysa kullanıcıya bilgi verilebilir
                    // Örneğin: Toast.makeText(this@CargoReportActivity, "Kayıtlı villa bulunamadı.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onItemSelected(item: SearchableItem, canceled: Boolean) {
        if (canceled) {
            // İptal durumu
            return
        }

        when (binding.chipGroupMode.checkedChipId) {
            binding.chipFilterByCompany.id -> {
                selectedCargoCompany = item as? CargoCompany
                binding.tvSelectionPicker.text = selectedCargoCompany?.getDisplayName()
                // TODO: Update your report fetching logic if needed
                fetchReportData() // Filtreleme için ana veriyi yeniden çek
            }
            binding.chipFilterByVilla.id -> {
                selectedVilla = item as? Villa
                binding.tvSelectionPicker.text = selectedVilla?.getDisplayName()
                // TODO: Update your report fetching logic if needed
                fetchReportData() // Filtreleme için ana veriyi yeniden çek
            }
        }
    }

    // RecyclerView ve Adapter'ı kurar
    private fun setupRecyclerView() {
        reportAdapter = CargoReportAdapter()
        binding.rvResult.layoutManager = LinearLayoutManager(this)
        binding.rvResult.adapter = reportAdapter
    }

    // Arayüzdeki tüm dinleyicileri (tıklama vb.) kurar
    private fun setupListeners() {
        binding.blurBack.setOnClickListener {
            finish()
        }

        binding.chipGroupMode.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val selectedChipId = checkedIds.first()
            if (selectedChipId == R.id.chip_filter_by_company) {
                currentFilterMode = CargoReportAdapter.ReportMode.BY_COMPANY
                reportAdapter.currentMode = CargoReportAdapter.ReportMode.BY_COMPANY
                binding.tvSelectionPicker.text = "Şirket Seçin"
                selectedVilla = null
            } else {
                currentFilterMode = CargoReportAdapter.ReportMode.BY_VILLA
                reportAdapter.currentMode = CargoReportAdapter.ReportMode.BY_VILLA
                binding.tvSelectionPicker.text = "Villa Seçin"
                selectedCargoCompany = null
            }
            fetchReportData()
        }

        binding.tvSelectionPicker.setOnClickListener {
            setupPickers()
        }

        binding.layoutStartDate.setOnClickListener {
            showDatePickerDialog(true)
        }
        binding.layoutEndDate.setOnClickListener {
            showDatePickerDialog(false)
        }
    }

    // Tarih seçicileri başlangıç ve bitiş tarihi olarak bugüne ayarlar
    private fun setupInitialDates() {
        // Başlangıcı günün başına al (00:00:00)
        startDate.set(Calendar.HOUR_OF_DAY, 0)
        startDate.set(Calendar.MINUTE, 0)
        startDate.set(Calendar.SECOND, 0)

        // Bitişi günün sonuna al (23:59:59)
        endDate.set(Calendar.HOUR_OF_DAY, 23)
        endDate.set(Calendar.MINUTE, 59)
        endDate.set(Calendar.SECOND, 59)

        updateDateButtonTexts()
    }

    private fun showDatePickerDialog(isStartDate: Boolean) {
        val initialCalendar = if (isStartDate) startDate else endDate
        val datePicker = CustomDatePickerFragment.newInstance(initialCalendar)

        datePicker.setOnDateSelectedListener(object : CustomDatePickerFragment.OnDateSelectedListener {
            override fun onDateSelected(calendar: Calendar) {
                if (isStartDate) {
                    startDate = calendar
                    startDate.set(Calendar.HOUR_OF_DAY, 0)
                    startDate.set(Calendar.MINUTE, 0)
                } else {
                    endDate = calendar
                    endDate.set(Calendar.HOUR_OF_DAY, 23)
                    endDate.set(Calendar.MINUTE, 59)
                }
                updateDateButtonTexts()
                fetchReportData()
            }
        })

        datePicker.show(supportFragmentManager, "CustomDatePicker")
    }

    private fun updateDateButtonTexts() {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("tr"))
        binding.textPickedStartDate.text = sdf.format(startDate.time)
        binding.textPickedEndDate.text = sdf.format(endDate.time)
    }

    private fun formatDateToISO(calendar: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(calendar.time)
    }

    private fun fetchReportData() {
        val companyIdFilter = selectedCargoCompany?.companyId
        val villaIdFilter = selectedVilla?.villaId

        val startDateString = formatDateToISO(startDate)
        val endDateString = formatDateToISO(endDate)

        lifecycleScope.launch {
            db.cargoDao().getCargoReportDetailsFiltered(
                startDate = startDateString,
                endDate = endDateString,
                companyIdFilter = companyIdFilter,
                villaIdFilter = villaIdFilter
            ).collectLatest { reportList ->
                reportAdapter.differ.submitList(reportList.toList())
                // İsteğe bağlı: Liste boşsa bir mesaj gösterilebilir
                // binding.tvEmptyReport.visibility = if (reportList.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}