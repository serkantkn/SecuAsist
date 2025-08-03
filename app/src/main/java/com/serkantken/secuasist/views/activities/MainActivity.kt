package com.serkantken.secuasist.views.activities

//noinspection SuspiciousImport
import android.R
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.Gson
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.adapters.ContactsAdapter
import com.serkantken.secuasist.adapters.MainTabsAdapter
import com.serkantken.secuasist.adapters.MainViewPagerAdapter
import com.serkantken.secuasist.adapters.StreetFilterAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.ActivityMainBinding
import com.serkantken.secuasist.databinding.DialogAddEditCargoCompanyBinding
import com.serkantken.secuasist.databinding.DialogAddEditContactBinding
import com.serkantken.secuasist.databinding.DialogAddEditVillaBinding
import com.serkantken.secuasist.databinding.DialogFilterByStatusBinding
import com.serkantken.secuasist.databinding.DialogFilterByStreetBinding
import com.serkantken.secuasist.databinding.DialogManageVillaContactsBinding
import com.serkantken.secuasist.databinding.LayoutBalloonVillaInfoBinding
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaContact
import com.serkantken.secuasist.network.CargoCompanyDto
import com.serkantken.secuasist.network.ContactDto
import com.serkantken.secuasist.network.VillaContactDeleteDto
import com.serkantken.secuasist.network.VillaContactDto
import com.serkantken.secuasist.network.VillaDto
import com.serkantken.secuasist.network.WebSocketClient
import com.serkantken.secuasist.network.WebSocketMessage
import com.serkantken.secuasist.network.toCargoCompany
import com.serkantken.secuasist.network.toContact
import com.serkantken.secuasist.network.toVilla
import com.serkantken.secuasist.network.toVillaContact
import com.serkantken.secuasist.utils.Tools
import com.serkantken.secuasist.views.fragments.CargoFragment
import com.serkantken.secuasist.views.fragments.ContactsFragment
import com.serkantken.secuasist.views.fragments.SelectCargoRecipientsSheet
import com.serkantken.secuasist.views.fragments.VillaFragment
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.createBalloon
import eightbitlab.com.blurview.BlurView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var appDatabase: AppDatabase
    private lateinit var mainTabsAdapter: MainTabsAdapter // EKLEYİN
    private val tabsList = mutableListOf<MainTab>() // EKLEYİN
    private val gson = Gson() // Gson instance'ı
    private lateinit var csvFilePickerLauncher: ActivityResultLauncher<String>
    private var isBalloonShowing = false
    private lateinit var balloonSortVillas: Balloon
    private var isSearchModeActive = false

    private var activityScopeJob: Job? = null
    private val activityScope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Main + (activityScopeJob ?: Job()))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        csvFilePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { processCsvFile(it) } ?: run { showToast("Dosya seçilmedi.") }
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top + Tools(this).convertDpToPixel(5), 0, 0)
            binding.bottomNavBar.setPadding(Tools(this).convertDpToPixel(16), 0, Tools(this).convertDpToPixel(16), systemBars.bottom)
            insets
        }

        Tools(this).blur(arrayOf(binding.blurFab, binding.blurToolbarButtons, binding.blurMessage, binding.blurNavView), 10f, true)
        appDatabase = AppDatabase.Companion.getDatabase(this)
        webSocketClient = (application as SecuAsistApplication).webSocketClient

        setupViewPager()
        setupMainTabsRecyclerView()
        setupListeners()
        observeWebSocketMessages()

    }

    data class MainTab(
        val id: Int, // Benzersiz bir kimlik (ViewPager'ın pozisyonuyla eşleşebilir)
        val title: String,
        val iconResId: Int, // Drawable resource ID'si
        var isSelected: Boolean = false
    )

    private fun setupViewPager() {
        binding.viewPager.adapter = MainViewPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 4
        binding.viewPager.isUserInputEnabled = false // Kullanıcı kaydırmasını devre dışı bırak

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                if (isSearchModeActive) {
                    toggleSearchMode(false)
                }

                mainTabsAdapter.selectTab(position) // Adapter'da sekmeyi seçili işaretle

                val layoutManager = binding.rvMainTabs.layoutManager as LinearLayoutManager

                val smoothScroller = object : LinearSmoothScroller(binding.rvMainTabs.context) {
                    override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                        // viewStart: Öğenin sol kenarı
                        // viewEnd: Öğenin sağ kenarı
                        // boxStart: RecyclerView'ın sol padding sonrası başlangıcı
                        // boxEnd: RecyclerView'ın sağ padding öncesi sonu

                        return when (targetPosition) { // targetPosition, smoothScroller.targetPosition'dan gelir
                            0 -> { // İlk sekme
                                boxStart - viewStart // Öğenin başını RecyclerView'ın başına hizala
                            }
                            mainTabsAdapter.itemCount - 1 -> { // Son sekme
                                boxEnd - viewEnd // Öğenin sonunu RecyclerView'ın sonuna hizala
                            }
                            else -> { // Ortadaki sekmeler
                                // Öğeyi RecyclerView'ın ortasına hizala
                                (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
                            }
                        }
                    }

                    // İsteğe bağlı: Kaydırma hızını ayarlayabilirsiniz
                    override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                        return 75f / displayMetrics.densityDpi // Değeri artırdıkça yavaşlar, azalttıkça hızlanır
                    }
                }

                smoothScroller.targetPosition = position
                layoutManager.startSmoothScroll(smoothScroller)

                updateToolbarTitle(position)
                updateMainActionButton(position)
            }
        })
    }

    private fun setupMainTabsRecyclerView() {
        // Tab verilerini oluştur
        tabsList.clear()
        tabsList.add(MainTab(0, "Villa Listesi", com.serkantken.secuasist.R.drawable.ic_home, true)) // İlk tab seçili başlasın
        tabsList.add(MainTab(1, "Kişi Listesi", com.serkantken.secuasist.R.drawable.ic_person, false))
        tabsList.add(MainTab(2, "Kargo Yönetimi", com.serkantken.secuasist.R.drawable.ic_cargo, false))
        tabsList.add(MainTab(3, "Arıza Takibi", com.serkantken.secuasist.R.drawable.ic_videocam, false))
        // İkonları kendi projenizdeki drawable'lar ile değiştirin (ic_home, ic_cargo vb.)

        mainTabsAdapter = MainTabsAdapter { clickedTab ->
            binding.viewPager.setCurrentItem(clickedTab.id, false)
            // SnapHelper zaten buraya kaydıracaktır, ama emin olmak için:
            // (binding.rvMainTabs.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(clickedTab.id, 0)
        }

        binding.rvMainTabs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = mainTabsAdapter
            // SnapHelper ekleme
            //val snapHelper = PagerSnapHelper() // Veya LinearSnapHelper deneyebilirsiniz
            //snapHelper.attachToRecyclerView(this)
        }
        mainTabsAdapter.submitList(tabsList)
        mainTabsAdapter.selectTab(0) // Başlangıçta ilk tab seçili
    }

    private fun updateMainActionButton(fragmentPosition: Int) {
        val shouldShowFab = when (fragmentPosition) {
            0 -> true
            1 -> true
            2 -> true
            3 -> false
            else -> false
        }

        // İkonu ve tıklama olayını her zaman fragment'a göre güncelle
        // (görünür olup olmamasından bağımsız olarak, çünkü animasyon sırasında değişebilir)
        if (shouldShowFab) {
            when (fragmentPosition) {
                0 -> {
                    binding.icFab.setImageResource(com.serkantken.secuasist.R.drawable.ic_filter)
                    binding.icFab.setOnClickListener { showBalloonSortVillas(it)  }
                    binding.btnSearch.visibility = View.VISIBLE
                    binding.etSearch.hint = "Villa ara"
                }
                1 -> {
                    binding.icFab.setImageResource(com.serkantken.secuasist.R.drawable.ic_add)
                    binding.icFab.setOnClickListener { showAddEditContactDialog(null, null) }
                    binding.btnSearch.visibility = View.VISIBLE
                    binding.etSearch.hint = "Kişi ara"
                }
                2 -> {
                    binding.icFab.setImageResource(com.serkantken.secuasist.R.drawable.ic_add)
                    binding.icFab.setOnClickListener { showAddEditCargoCompanyDialog(null) }
                    binding.btnSearch.visibility = View.VISIBLE
                    binding.etSearch.hint = "Kargo şirketi ara"
                }
                // 'else' gerekmiyor çünkü shouldShowFab zaten bu durumları kapsıyor.
            }
        }

        val currentVisibility = binding.blurFab.visibility
        val targetVisibility = if (shouldShowFab) View.VISIBLE else View.GONE

        if (currentVisibility != targetVisibility) {
            if (targetVisibility == View.VISIBLE) {
                // ARAMA BUTONUNU GÖSTERME ANİMASYONU
                binding.blurFab.alpha = 0f // Başlangıçta tamamen şeffaf yap

                // TransitionManager'ı, sekme çubuğunun (blur_nav_view) küçülmesini anime etmek için hazırla.
                // blurSearchButton'ı VISIBLE yapmak, kaplayacağı alanı almasını sağlar ve bu küçülmeyi tetikler.
                TransitionManager.beginDelayedTransition(binding.root as ViewGroup,
                    ChangeBounds().setDuration(300))
                binding.blurFab.visibility = View.VISIBLE // Alanı kaplaması için görünür yap
                binding.btnSearch.visibility = View.VISIBLE

                // Arama butonunu yavaşça görünür yap (fade in)
                binding.blurFab.animate().alpha(1f).setDuration(200).start()

            } else {
                // ARAMA BUTONUNU GİZLEME ANİMASYONU
                // Arama butonunu yavaşça şeffaflaştır (fade out)
                binding.blurFab.animate().alpha(0f).setDuration(200)
                    .withEndAction {
                        // Şeffaflaşma bittikten sonra butonu GONE yap.
                        // TransitionManager, sekme çubuğunun (blur_nav_view) genişlemesini anime edecek.
                        TransitionManager.beginDelayedTransition(binding.root as ViewGroup,
                            ChangeBounds().setDuration(300))
                        binding.blurFab.visibility = View.GONE
                        binding.btnSearch.visibility = View.GONE
                    }.start()
            }
        } else if (targetVisibility == View.VISIBLE && binding.blurFab.alpha != 1f) {
            // Eğer zaten görünür olması gerekiyor ama bir şekilde tam opak değilse
            // (örneğin animasyon yarıda kesilmişse), hızlıca opak yap.
            binding.blurFab.animate().alpha(1f).setDuration(100).start()
        }
    }

    private fun updateToolbarTitle(position: Int) {
        binding.contentTitle.text = when (position) {
            0 -> "Villa Listesi"
            1 -> "Kişi Listesi"
            2 -> "Kargo Yönetimi"
            3 -> "Arıza Takibi"
            else -> "SecuAsist"
        }
    }

    private fun toggleSearchMode(isActive: Boolean) {
        isSearchModeActive = isActive
        if (isActive) {
            // ARAMA MODUNU AÇ
            binding.btnCancel.visibility = View.VISIBLE
            binding.contentTitle.visibility = View.GONE
            binding.btnSearch.visibility = View.GONE
            binding.btnMore.visibility = View.GONE
            binding.blurSearchbox.visibility = View.VISIBLE

            // Klavyeyi otomatik aç ve arama kutusuna odaklan
            binding.etSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)

        } else {
            // ARAMA MODUNU KAPAT
            binding.contentTitle.visibility = View.VISIBLE
            binding.btnSearch.visibility = View.VISIBLE
            binding.btnMore.visibility = View.VISIBLE
            binding.blurSearchbox.visibility = View.GONE
            binding.btnCancel.visibility = View.GONE

            // Arama kutusunu temizle ve filtreyi sıfırla
            binding.etSearch.text?.clear()
            val villaFragment = supportFragmentManager.findFragmentByTag("f0") as? VillaFragment
            villaFragment?.setSearchQuery("")

            // Klavyeyi gizle
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }

    private fun setupListeners() {
        binding.btnSearch.setOnClickListener {
            toggleSearchMode(true)
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isSearchModeActive) return

                val query = s.toString()
                val currentItem = binding.viewPager.currentItem

                // Aktif olan fragment'a göre sorguyu yönlendir
                when (currentItem) {
                    0 -> {
                        val fragment = supportFragmentManager.findFragmentByTag("f$currentItem") as? VillaFragment
                        fragment?.setSearchQuery(query)
                    }
                    1 -> {
                        val fragment = supportFragmentManager.findFragmentByTag("f$currentItem") as? ContactsFragment
                        fragment?.setSearchQuery(query)
                    }
                    2 -> {
                        val fragment = supportFragmentManager.findFragmentByTag("f$currentItem") as? CargoFragment
                        fragment?.setSearchQuery(query)
                    }
                    // Diğer fragment'larda arama olmadığı için case eklemeye gerek yok.
                }
            }
        })

        binding.btnCancel.setOnClickListener {
            toggleSearchMode(false)
        }

        binding.btnMore.setOnClickListener {
            val balloon: Balloon
            if (binding.viewPager.currentItem == 0) {
                balloon = createBalloon(this) {
                    setLayout(com.serkantken.secuasist.R.layout.layout_balloon_more_options_villa)
                    setArrowSize(0)
                    setWidth(BalloonSizeSpec.WRAP)
                    setHeight(BalloonSizeSpec.WRAP)
                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.transparent))
                    setBalloonAnimation(BalloonAnimation.CIRCULAR)
                    setDismissWhenTouchOutside(true)
                    setLifecycleOwner(this@MainActivity)
                    build()
                }
                val blur = balloon.getContentView().findViewById<BlurView>(com.serkantken.secuasist.R.id.layout_menu_balloon)
                val btnNewVilla = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_new_villa)
                val btnImport = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_import)
                Tools(this@MainActivity).blur(arrayOf(blur), 10f, true)
                binding.toolbarButtonLayout.visibility = View.INVISIBLE
                balloon.showAlignTop(binding.toolbarButtonLayout, 90, it.height+50)
                balloon.setOnBalloonDismissListener {
                    binding.toolbarButtonLayout.visibility = View.VISIBLE
                }
                btnNewVilla.setOnClickListener {
                    showAddEditVillaDialog(null)
                    balloon.dismiss()
                }
                btnImport.setOnClickListener {
                    launchCsvFilePicker()
                    balloon.dismiss()
                }
            }
        }
    }

    fun showBalloonSortVillas(view: View) {
        if (binding.viewPager.currentItem != 0) return
        if (isBalloonShowing) {
            balloonSortVillas.dismiss()
            isBalloonShowing = false
        } else {
            balloonSortVillas = createBalloon(this) {
                setLayout(com.serkantken.secuasist.R.layout.layout_balloon_sort_villas)
                setArrowSize(0)
                setWidth(BalloonSizeSpec.WRAP)
                setHeight(BalloonSizeSpec.WRAP)
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.transparent))
                setBalloonAnimation(BalloonAnimation.CIRCULAR)
                setDismissWhenTouchOutside(true)
                setLifecycleOwner(this@MainActivity)
                build()
            }

            val contentView = balloonSortVillas.getContentView()
            val blurView = contentView.findViewById<BlurView>(com.serkantken.secuasist.R.id.layout_menu_balloon)
            Tools(this@MainActivity).blur(arrayOf(blurView), 10f, true)
            binding.icFab.setImageResource(com.serkantken.secuasist.R.drawable.ic_close)
            isBalloonShowing = true
            balloonSortVillas.showAlignTop(binding.blurFab, 0, Tools(this@MainActivity).convertDpToPixel(-10))
            balloonSortVillas.setOnBalloonDismissListener {
                binding.icFab.setImageResource(com.serkantken.secuasist.R.drawable.ic_filter)
            }

            val villaFragment = supportFragmentManager.findFragmentByTag("f" + binding.viewPager.currentItem) as? VillaFragment

            contentView.findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_filter_by_street).setOnClickListener {
                // Bu fonksiyonu bir sonraki adımda oluşturacağız
                showStreetFilterDialog()
                balloonSortVillas.dismiss()
            }

            contentView.findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_filter_by_status).setOnClickListener {
                showStatusFilterDialog()
                balloonSortVillas.dismiss()
            }

            contentView.findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_clear_filter).setOnClickListener {
                villaFragment?.clearFilters() // Bu fonksiyonu VillaFragment'a ekleyeceğiz
                showToast("Tüm filtreler temizlendi.")
                balloonSortVillas.dismiss()
            }
        }
    }

    private fun showStreetFilterDialog() {
        // VillaFragment'ın o anki örneğini bul
        val villaFragment = supportFragmentManager.findFragmentByTag("f0") as? VillaFragment ?: return
        val appDatabase = AppDatabase.getDatabase(this)

        // Veritabanından benzersiz sokak adlarını çek.
        lifecycleScope.launch {
            // Flow'dan sadece ilk listeyi alıp devam etmek, diyaloglar için daha verimlidir.
            val streetList = appDatabase.villaDao().getUniqueStreetNames().first()

            if (streetList.isEmpty()) {
                showToast("Filtrelenecek sokak bulunamadı.")
                return@launch
            }

            // Yeni özel diyalog layout'umuzu inflate et
            val dialogBinding = DialogFilterByStreetBinding.inflate(layoutInflater)
            Tools(this@MainActivity).blur(arrayOf(dialogBinding.root), 10f, true)

            // AlertDialog'u oluştur
            val alertDialog = AlertDialog.Builder(this@MainActivity)
                .setView(dialogBinding.root)
                .create()

            // StreetFilterAdapter'ı oluştur.
            // Bir sokağa tıklandığında ne olacağını doğrudan burada tanımlıyoruz (lambda).
            val streetAdapter = StreetFilterAdapter(streetList) { selectedStreet ->
                villaFragment.setStreetFilter(selectedStreet) // Fragment'a filtreyi uygulat
                showToast("$selectedStreet sokağındaki villalar listeleniyor.")
                alertDialog.dismiss() // Diyalogu kapat
            }

            // RecyclerView'ı ayarla
            dialogBinding.rvStreetList.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = streetAdapter
            }

            // Kapatma butonu
            dialogBinding.btnClose.setOnClickListener {
                alertDialog.dismiss()
            }

            // Diyalog penceresinin arkaplanını şeffaf yapıp göster
            alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            alertDialog.show()
        }
    }

    private fun showStatusFilterDialog() {
        // VillaFragment'ın o anki örneğini bul ve filtre durumunu al
        val villaFragment = supportFragmentManager.findFragmentByTag("f0") as? VillaFragment ?: return
        val currentFilters = villaFragment.getCurrentFilterState().activeStatusFilters
        val dialogBinding = DialogFilterByStatusBinding.inflate(layoutInflater)
        Tools(this).blur(arrayOf(dialogBinding.root), 10f, true)

        // Mevcut aktif filtrelere göre CheckBox'ları işaretle
        dialogBinding.cbFilterUnderConstruction.isChecked = VillaFragment.StatusFilter.UNDER_CONSTRUCTION in currentFilters
        dialogBinding.cbFilterIsSpecial.isChecked = VillaFragment.StatusFilter.IS_SPECIAL in currentFilters
        dialogBinding.cbFilterIsRental.isChecked = VillaFragment.StatusFilter.IS_RENTAL in currentFilters
        dialogBinding.cbFilterNoCargoCalls.isChecked = VillaFragment.StatusFilter.NO_CARGO_CALLS in currentFilters

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        alertDialog.setOnShowListener {
            dialogBinding.btnApply.setOnClickListener {
                // Kullanıcının seçtiği CheckBox'lara göre yeni bir filtre seti oluştur
                val newStatusFilters = mutableSetOf<VillaFragment.StatusFilter>()
                if (dialogBinding.cbFilterUnderConstruction.isChecked) newStatusFilters.add(VillaFragment.StatusFilter.UNDER_CONSTRUCTION)
                if (dialogBinding.cbFilterIsSpecial.isChecked) newStatusFilters.add(VillaFragment.StatusFilter.IS_SPECIAL)
                if (dialogBinding.cbFilterIsRental.isChecked) newStatusFilters.add(VillaFragment.StatusFilter.IS_RENTAL)
                if (dialogBinding.cbFilterNoCargoCalls.isChecked) newStatusFilters.add(VillaFragment.StatusFilter.NO_CARGO_CALLS)

                // Yeni filtre setini VillaFragment'a gönder
                villaFragment.setStatusFilters(newStatusFilters)

                showToast("Filtreler uygulandı.")
                alertDialog.dismiss()
            }

            dialogBinding.btnClose.setOnClickListener {
                alertDialog.dismiss()
            }
        }

        alertDialog.window?.setBackgroundDrawableResource(R.color.transparent)
        alertDialog.show()
    }

    // YENİ: Dosya seçiciyi başlatan fonksiyon
    private fun launchCsvFilePicker() {
        csvFilePickerLauncher.launch("text/csv")
    }

    // YENİ: Seçilen dosyayı işleyecek fonksiyon (şimdilik placeholder)
    private fun processCsvFile(fileUri: Uri) {
        activityScope.launch(Dispatchers.IO) { // Dosya okuma ve DB işlemleri için IO thread'i
            val villaListesi = mutableListOf<Villa>()
            var hataliSatirSayisi = 0

            try {
                contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        // Eğer CSV'nin ilk satırı başlık ise, .drop(1) ekleyerek atlayabilirsiniz
                        // Örnek: reader.readLines().drop(1).forEach { line ->
                        reader.readLines().forEach { line ->
                            if (line.isBlank()) return@forEach // Boş satırları atla

                            try {
                                // Tırnak içindeki virgülleri korumak için basit bir regex ile ayırma
                                val columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                                    .map { it.trim().removeSurrounding("\"") }

                                val villa = Villa(
                                    villaId = 0,
                                    villaNo = columns.getOrNull(0)?.toIntOrNull() ?: throw IllegalArgumentException("VillaNo Hatalı"),
                                    villaNotes = columns.getOrNull(1),
                                    villaStreet = columns.getOrNull(2),
                                    villaNavigationA = columns.getOrNull(3),
                                    villaNavigationB = columns.getOrNull(4),
                                    isVillaUnderConstruction = columns.getOrNull(5)?.toIntOrNull() ?: 0,
                                    isVillaSpecial = columns.getOrNull(6)?.toIntOrNull() ?: 0,
                                    isVillaRental = columns.getOrNull(7)?.toIntOrNull() ?: 0,
                                    isVillaCallFromHome = columns.getOrNull(8)?.toIntOrNull() ?: 0,
                                    isVillaCallForCargo = columns.getOrNull(9)?.toIntOrNull() ?: 0,
                                    isVillaEmpty = columns.getOrNull(10)?.toIntOrNull() ?: 0
                                )
                                villaListesi.add(villa)
                            } catch (e: Exception) {
                                // Satırda format hatası varsa (örn: villaNo sayı değilse)
                                hataliSatirSayisi++
                            }
                        }
                    }
                }

                if (villaListesi.isNotEmpty()) {
                    val sonuc = appDatabase.villaDao().insertAll(villaListesi)
                    val basariliEklemeSayisi = sonuc.count { it != -1L }
                    val atlananKayitSayisi = sonuc.size - basariliEklemeSayisi

                    // Sonucu ana thread'de göster
                    launch(Dispatchers.Main) {
                        var message = "$basariliEklemeSayisi yeni villa eklendi."
                        if (atlananKayitSayisi > 0) message += " $atlananKayitSayisi villa zaten mevcut olduğu için atlandı."
                        if (hataliSatirSayisi > 0) message += " $hataliSatirSayisi satır hatalı veri nedeniyle işlenemedi."
                        showToast(message)
                    }
                } else {
                    launch(Dispatchers.Main) {
                        showToast("CSV dosyasında işlenecek geçerli veri bulunamadı.")
                    }
                }

            } catch (e: Exception) {
                // Genel dosya okuma hatası
                launch(Dispatchers.Main) {
                    showToast("Dosya okunurken bir hata oluştu: ${e.message}")
                }
            }
        }
    }

    private fun observeWebSocketMessages() {
        activityScope.launch {
            webSocketClient.incomingMessages.collect { jsonMessage ->
                // Gelen mesajın durum mesajı olup olmadığını kontrol et
                if (jsonMessage.startsWith("STATUS:")) {
                    when (jsonMessage) {
                        "STATUS:CONNECTED" -> showToast("WebSocket Bağlandı.")
                        "STATUS:DISCONNECTED" -> showToast("WebSocket Bağlantısı Kesildi. Yeniden bağlanıyor...")
                        // ... diğer durumlar ...
                    }
                    return@collect // Durum mesajıysa, işlemi burada bitir.
                }

                // Gelen JSON'ı işlemeyi dene
                try {
                    // 1. Genel mesaj yapısını (type, data) parse et
                    val webSocketMessage = gson.fromJson(jsonMessage, WebSocketMessage::class.java)

                    // 2. Mesajın tipine göre ilgili veritabanı işlemini yap
                    //    Tüm veritabanı işlemleri için IO thread'ine geç.
                    launch(Dispatchers.IO) {
                        when (webSocketMessage.type) {
                            // --- VİLLA İŞLEMLERİ ---
                            "add_villa", "update_villa" -> {
                                val villaDto = gson.fromJson(webSocketMessage.data.toString(), VillaDto::class.java)
                                // DTO'yu veritabanı entity'sine çevir (bunu yapmak için bir extension function yazılabilir)
                                val villa = villaDto.toVilla() // Bu fonksiyonu birazdan ekleyeceğiz
                                appDatabase.villaDao().insert(villa) // insert hem ekler hem günceller (OnConflictStrategy.REPLACE)
                                launch(Dispatchers.Main) { showToast("Villa ${villa.villaNo} uzaktan güncellendi.") }
                            }
                            "delete_villa" -> {
                                val deleteDto = gson.fromJson(webSocketMessage.data.toString(), VillaDto::class.java)
                                deleteDto.villaId?.let {
                                    appDatabase.villaDao().deleteById(it)
                                    launch(Dispatchers.Main) { showToast("Bir villa uzaktan silindi.") }
                                }
                            }

                            // --- KİŞİ İŞLEMLERİ ---
                            "add_contact", "update_contact" -> {
                                val contactDto = gson.fromJson(webSocketMessage.data.toString(), ContactDto::class.java)
                                val contact = contactDto.toContact()
                                appDatabase.contactDao().insert(contact)
                                launch(Dispatchers.Main) { showToast("${contact.contactName} uzaktan güncellendi.") }
                            }
                            "delete_contact" -> {
                                val deleteDto = gson.fromJson(webSocketMessage.data.toString(), ContactDto::class.java)
                                deleteDto.contactId?.let {
                                    appDatabase.contactDao().deleteById(it)
                                    launch(Dispatchers.Main) { showToast("Bir kişi uzaktan silindi.") }
                                }
                            }

                            // --- VİLLA-KİŞİ İLİŞKİSİ İŞLEMLERİ ---
                            "add_villacontact" -> {
                                val vcDto = gson.fromJson(webSocketMessage.data.toString(), VillaContactDto::class.java)
                                val villaContact = vcDto.toVillaContact()
                                appDatabase.villaContactDao().insert(villaContact)
                            }
                            "delete_villacontact" -> {
                                val deleteDto = gson.fromJson(webSocketMessage.data.toString(), VillaContactDeleteDto::class.java)
                                appDatabase.villaContactDao().deleteByVillaIdAndContactId(deleteDto.villaId, deleteDto.contactId)
                            }

                            // --- KARGO ŞİRKETİ İŞLEMLERİ (Örnek) ---
                            "add_cargo_company", "update_cargo_company" -> {
                                val companyDto = gson.fromJson(webSocketMessage.data.toString(), CargoCompanyDto::class.java)
                                val company = companyDto.toCargoCompany()
                                appDatabase.cargoCompanyDao().insert(company)
                                launch(Dispatchers.Main) { showToast("${company.companyName} uzaktan güncellendi.") }
                            }
                            "delete_cargo_company" -> {
                                val deleteDto = gson.fromJson(webSocketMessage.data.toString(), CargoCompanyDto::class.java)
                                deleteDto.companyId?.let {
                                    appDatabase.cargoCompanyDao().deleteById(it)
                                    launch(Dispatchers.Main) { showToast("Bir kargo şirketi uzaktan silindi.") }
                                }
                            }

                            else -> {
                                // Bilinmeyen bir mesaj tipi gelirse
                                launch(Dispatchers.Main) { showToast("Bilinmeyen mesaj tipi: ${webSocketMessage.type}") }
                            }
                        }
                    }

                } catch (e: Exception) {
                    // JSON parse hatası veya başka bir hata olursa
                    launch(Dispatchers.Main) {
                        showToast("Sunucudan gelen mesaj işlenemedi: ${e.message}")
                    }
                }
            }
        }
    }

    fun showAddEditVillaDialog(villaToEdit: Villa?) {
        val dialogBinding = DialogAddEditVillaBinding.inflate(layoutInflater)
        val isEditMode = villaToEdit != null
        Tools(this).blur(arrayOf(dialogBinding.blurWindow), 10f, true)

        val streets = resources.getStringArray(com.serkantken.secuasist.R.array.street_names)
        val streetArrayAdapter = ArrayAdapter(this, R.layout.simple_dropdown_item_1line, streets)
        dialogBinding.actvVillaStreet.setAdapter(streetArrayAdapter)

        if (isEditMode) {
            dialogBinding.windowTitle.text = "Villa Düzenle"
            dialogBinding.etVillaNo.setText(villaToEdit?.villaNo?.toString())
            dialogBinding.etVillaNotes.setText(villaToEdit?.villaNotes)
            dialogBinding.actvVillaStreet.setText(villaToEdit?.villaStreet, false)
            dialogBinding.etVillaNavigationA.setText(villaToEdit?.villaNavigationA)
            dialogBinding.etVillaNavigationB.setText(villaToEdit?.villaNavigationB)
            dialogBinding.cbIsUnderConstruction.isChecked = villaToEdit?.isVillaUnderConstruction == 1
            dialogBinding.cbIsSpecial.isChecked = villaToEdit?.isVillaSpecial == 1
            dialogBinding.cbIsRental.isChecked = villaToEdit?.isVillaRental == 1
            dialogBinding.cbIsCallFromHome.isChecked = villaToEdit?.isVillaCallFromHome == 1
            dialogBinding.cbIsCallForCargo.isChecked = villaToEdit?.isVillaCallForCargo == 1
            dialogBinding.cbIsEmpty.isChecked = villaToEdit?.isVillaEmpty == 1
            dialogBinding.etVillaNo.isEnabled = false
            dialogBinding.btnVillaContacts.visibility = View.VISIBLE
        } else {
            dialogBinding.windowTitle.text = "Villa Ekle"
            dialogBinding.btnVillaContacts.visibility = View.GONE
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        alertDialog.setOnShowListener {
            dialogBinding.btnSave.setOnClickListener {
                val villaNo = dialogBinding.etVillaNo.text.toString().toIntOrNull()
                if (villaNo == null) {
                    showToast("Villa Numarası geçerli değil.")
                    return@setOnClickListener
                }

                val selectedStreet = dialogBinding.actvVillaStreet.text.toString()
                if (selectedStreet.isBlank()) {
                    showToast("Lütfen bir sokak seçin.")
                    return@setOnClickListener
                }

                val villa = Villa(
                    villaId = villaToEdit?.villaId ?: 0,
                    villaNo = villaNo,
                    villaNotes = dialogBinding.etVillaNotes.text.toString().takeIf { it.isNotBlank() },
                    villaStreet = selectedStreet,
                    villaNavigationA = dialogBinding.etVillaNavigationA.text.toString().takeIf { it.isNotBlank() },
                    villaNavigationB = dialogBinding.etVillaNavigationB.text.toString().takeIf { it.isNotBlank() },
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
                        // WebSocket güncellemesi burada olabilir
                    } else {
                        val existingVilla = appDatabase.villaDao().getVillaByNo(villa.villaNo)
                        if (existingVilla == null) {
                            appDatabase.villaDao().insert(villa)
                            showToast("Villa ${villa.villaNo} eklendi.")
                            // WebSocket güncellemesi burada olabilir
                        } else {
                            showToast("Bu villa numarası zaten mevcut.")
                        }
                    }
                    alertDialog.dismiss()
                }
            }

            dialogBinding.btnVillaContacts.setOnClickListener {
                villaToEdit?.let { showManageVillaContactsDialog(it) }
                alertDialog.dismiss()
            }

            dialogBinding.btnClose.setOnClickListener {
                alertDialog.dismiss()
            }
        }

        alertDialog.window?.setBackgroundDrawableResource(R.color.transparent)
        alertDialog.show()
    }

    fun showVillaInfoBalloon(villa: Villa) {
        val dialogBinding = LayoutBalloonVillaInfoBinding.inflate(layoutInflater)
        Tools(this).blur(arrayOf(dialogBinding.layoutVillaBalloon), 15f, true)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.setCanceledOnTouchOutside(false)

        dialogBinding.apply {
            windowTitle.text = "Villa ${villa.villaNo}"
            notesText.text = villa.villaNotes
            streetTitle.text = villa.villaStreet
            navigationText.text = villa.villaNavigationA
            cameraText.text = "Bulunamadı"
            rgNavigation.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    com.serkantken.secuasist.R.id.rb_gate_a -> navigationText.text = villa.villaNavigationA
                    com.serkantken.secuasist.R.id.rb_gate_b -> navigationText.text = villa.villaNavigationB
                }
            }
            btnClose.setOnClickListener {
                dialog.dismiss()
            }
            btnEditVilla.setOnClickListener {
                showAddEditVillaDialog(villa)
                dialog.dismiss()
            }
            dialogBinding.rcVillaContacts.layoutManager = LinearLayoutManager(this@MainActivity)
            dialogBinding.rcVillaContacts.adapter = ContactsAdapter(
                onItemClick = { _, _ -> },
                onDeleteClick = { _, _ -> }
            )
            dialog.window?.setBackgroundDrawableResource(R.color.transparent)
            dialog.show()
        }
    }

    private fun showManageVillaContactsDialog(villa: Villa) {
        val dialogBinding = DialogManageVillaContactsBinding.inflate(layoutInflater)
        Tools(this).blur(arrayOf(dialogBinding.blur), 10f, true)

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
            it.visibility = View.INVISIBLE
            val balloon = createBalloon(this) {
                setLayout(com.serkantken.secuasist.R.layout.layout_balloon_new_villa_contact)
                setArrowSize(0)
                setWidth(BalloonSizeSpec.WRAP)
                setHeight(BalloonSizeSpec.WRAP)
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.transparent))
                setBalloonAnimation(BalloonAnimation.CIRCULAR)
                setLifecycleOwner(this@MainActivity)
                build()
            }
            balloon.showAlignTop(it, 50, it.height+10)
            val blur: BlurView = balloon.getContentView().findViewById(com.serkantken.secuasist.R.id.layout_menu_balloon)
            //Tools(this@MainActivity).blur(arrayOf(blur), 10f, true)
            balloon.setOnBalloonDismissListener { it.visibility = View.VISIBLE }
            val newContact: ConstraintLayout = balloon.getContentView().findViewById(com.serkantken.secuasist.R.id.btn_new_person)
            val existingContacts: ConstraintLayout = balloon.getContentView().findViewById(com.serkantken.secuasist.R.id.btn_existing_person)
            newContact.setOnClickListener {
                proceedWithNewContactCreationDialog(null, villa.villaId)
                balloon.dismiss()
            }
            existingContacts.setOnClickListener {
                showExistingContactsSelectionDialog(villa.villaId)
                balloon.dismiss()
            }
            //showAddEditContactDialog(null, villa.villaId)
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnClose.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.window?.setBackgroundDrawableResource(R.color.transparent)
        alertDialog.show()
    }

    fun showAddEditContactDialog(contactToEdit: Contact?, villaIdForAssociation: Int?) {
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

    private fun proceedWithNewContactCreationDialog(contactToEdit: Contact?, villaIdForAssociation: Int?, isEditModeOverride: Boolean? = null) {
        val dialogBinding = DialogAddEditContactBinding.inflate(layoutInflater)
        val isEditMode = isEditModeOverride ?: (contactToEdit != null)

        if (isEditMode) {
            dialogBinding.etContactName.setText(contactToEdit?.contactName)
            dialogBinding.etContactPhone.setText(contactToEdit?.contactPhone)
        }

        val dialogTitle = if (isEditMode) "Kişi Düzenle" else "Yeni Kişi Oluştur"
        val positiveButtonText = if (isEditMode) "Güncelle" else "Ekle"

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        Tools(this).blur(arrayOf(dialogBinding.blur), 15f, true)

        dialogBinding.windowTitle.text = dialogTitle
        dialogBinding.titleSave.text = positiveButtonText
        dialogBinding.btnSave.setOnClickListener {
            val contactName = dialogBinding.etContactName.text.toString().trim()
            val contactPhone = dialogBinding.etContactPhone.text.toString().trim()

            if (contactName.isBlank() || contactPhone.isBlank()) {
                val balloon = createBalloon(this@MainActivity) {
                    setText("Kişi adı ve telefon numarası boş olamaz.")
                    setArrowSize(5)
                    setWidthRatio(0.5f)
                    setHeight(BalloonSizeSpec.WRAP)
                    setTextSize(15f)
                    autoDismissDuration = 3000
                    build()
                }
                balloon.showAlignBottom(dialogBinding.btnSave)
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
        dialogBinding.btnClose.setOnClickListener {
            alertDialog.dismiss()
        }
        alertDialog.window?.setBackgroundDrawableResource(R.color.transparent)
        alertDialog.show()
    }

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

    internal fun showAddEditCargoCompanyDialog(companyToEdit: CargoCompany?) {
        val dialogBinding = DialogAddEditCargoCompanyBinding.inflate(layoutInflater)
        val isEditMode = companyToEdit != null
        Tools(this).blur(arrayOf(dialogBinding.blurWindow), 10f, true)

        if (isEditMode) {
            dialogBinding.windowTitle.text = "Kargo Şirketini Düzenle"
            dialogBinding.etCompanyName.setText(companyToEdit?.companyName)
        } else {
            dialogBinding.windowTitle.text = "Yeni Kargo Şirketi Ekle"
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnSave.setOnClickListener {
            val companyName = dialogBinding.etCompanyName.text.toString().trim()

            if (companyName.isEmpty()) {
                showToast("Kargo şirketi adı boş olamaz.")
                return@setOnClickListener
            }

            activityScope.launch {
                try {
                    if (isEditMode) {
                        val updatedCompany = companyToEdit!!.copy(companyName = companyName)
                        appDatabase.cargoCompanyDao().update(updatedCompany) // cargoDao'da update fonksiyonunuz olmalı
                        showToast("${updatedCompany.companyName} güncellendi.")
                        val cargoCompanyDto = CargoCompanyDto(
                            companyId = updatedCompany.companyId, // Güncelleme için ID gönderiliyor
                            companyName = updatedCompany.companyName
                            // isCargoInOperation ve contacts varsayılan değerlerini (0 ve null) alacak
                        )
                        val message = gson.toJson(WebSocketMessage("update_cargo_company", cargoCompanyDto))
                        webSocketClient.sendMessage(message)
                    } else {
                        // CargoCompany modelinizde companyId'nin autoGenerate olduğunu varsayıyorum.
                        // Bu yüzden önce DB'ye ekleyip ID'sini alıyoruz.
                        // Eğer companyId'yi sunucu veriyorsa veya farklı bir akış varsa burası değişebilir.
                        val tempNewCompany = CargoCompany(companyName = companyName)
                        val newGeneratedId = appDatabase.cargoCompanyDao().insert(tempNewCompany) // cargoDao'da insert fonksiyonunuz olmalı

                        // Başarılı bir şekilde eklendiğini ve ID aldığını varsayalım
                        if (newGeneratedId > 0) {
                            val newCompanyWithId = tempNewCompany.copy(companyId = newGeneratedId.toInt())
                            showToast("$companyName eklendi (ID: ${newCompanyWithId.companyId}).")

                            // WebSocket ile sunucuya ekleme mesajı gönder
                            val cargoCompanyDto = CargoCompanyDto(
                                // companyId yeni eklemede null gönderilebilir, sunucu ID atayacaksa.
                                // Ya da yerel ID'yi gönderebilirsiniz, sunucu bunu nasıl işleyeceğine bağlı.
                                // Şimdilik yerel ID'yi gönderelim, sunucu bunu dikkate almayabilir veya alabilir.
                                // Genelde "add" için client ID göndermez. Sunucunuzun beklentisine göre null yapabilirsiniz.
                                companyId = newCompanyWithId.companyId, // VEYA null, sunucunuzun beklentisine göre
                                companyName = newCompanyWithId.companyName
                                // isCargoInOperation ve contacts varsayılan değerlerini (0 ve null) alacak
                            )
                            val message = gson.toJson(WebSocketMessage("add_cargo_company", cargoCompanyDto))
                            webSocketClient.sendMessage(message)
                        } else {
                            showToast("Veritabanına eklenirken hata oluştu.")
                        }
                    }
                    alertDialog.dismiss()
                } catch (e: Exception) {
                    showToast("İşlem sırasında hata: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        dialogBinding.btnClose.setOnClickListener {
            alertDialog.dismiss()
        }

        // "Kargo Dağıtıcıları" butonu için şimdilik bir işlev atamıyoruz.
        // dialogBinding.btnCompanyContacts.setOnClickListener {
        //     showToast("Bu özellik yakında eklenecek.")
        // }

        alertDialog.show()
    }

    // Yeni eklenen fonksiyon
    fun showSelectCargoRecipientsSheet(companyId: Int) {
        val sheet = SelectCargoRecipientsSheet.newInstance(companyId)
        sheet.show(supportFragmentManager, SelectCargoRecipientsSheet.TAG)
    }

    private fun sendVillaAddOrUpdateToWebSocket(villa: Villa) {
        val type = if (villa.villaId > 0) "update_villa" else "add_villa"
        val villaDto = VillaDto(
            villaId = if (villa.villaId > 0) villa.villaId else null,
            villaNo = villa.villaNo,
            villaNotes = villa.villaNotes.takeIf { !it.isNullOrBlank() },
            villaStreet = villa.villaStreet.takeIf { !it.isNullOrBlank() },
            villaNavigationA = villa.villaNavigationA.takeIf { !it.isNullOrBlank() },
            villaNavigationB = null,
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

    fun sendContactDeleteToWebSocket(contactId: Int) {
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

    fun showToast(message: String) {
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
