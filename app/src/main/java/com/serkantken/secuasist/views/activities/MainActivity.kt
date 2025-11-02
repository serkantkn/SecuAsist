package com.serkantken.secuasist.views.activities

//noinspection SuspiciousImport
import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.adapters.AssignedDeliverersAdapter
import com.serkantken.secuasist.adapters.ContactsAdapter
import com.serkantken.secuasist.adapters.DraggableContactsAdapter
import com.serkantken.secuasist.adapters.MainTabsAdapter
import com.serkantken.secuasist.adapters.MainViewPagerAdapter
import com.serkantken.secuasist.adapters.StreetFilterAdapter
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.ActivityMainBinding
import com.serkantken.secuasist.databinding.DialogAddEditCargoCompanyBinding
import com.serkantken.secuasist.databinding.DialogAddEditContactBinding
import com.serkantken.secuasist.databinding.DialogFilterByStatusBinding
import com.serkantken.secuasist.databinding.DialogFilterByStreetBinding
import com.serkantken.secuasist.databinding.DialogManageCompanyDeliverersBinding
import com.serkantken.secuasist.databinding.DialogManageVillaContactsBinding
import com.serkantken.secuasist.databinding.DialogSelectContactBinding
import com.serkantken.secuasist.databinding.LayoutBalloonVillaInfoBinding
import com.serkantken.secuasist.databinding.LayoutDialogLoadingBinding
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.ContactFromPhone
import com.serkantken.secuasist.models.PhoneOptionForContact
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaContact
import com.serkantken.secuasist.network.ConnectionState
import com.serkantken.secuasist.network.WebSocketClient
import com.serkantken.secuasist.services.WhatsAppNotificationListener
import com.serkantken.secuasist.utils.Tools
import com.serkantken.secuasist.views.fragments.AddEditVillaDialogFragment
import com.serkantken.secuasist.views.fragments.CargoFragment
import com.serkantken.secuasist.views.fragments.ContactsFragment
import com.serkantken.secuasist.views.fragments.MultiNumberSelectionDialogFragment
import com.serkantken.secuasist.views.fragments.VillaFragment
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAlign
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.createBalloon
import com.skydoves.balloon.overlay.BalloonOverlayRoundRect
import eightbitlab.com.blurview.BlurView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

@ExperimentalBadgeUtils
class MainActivity : AppCompatActivity(),
    MultiNumberSelectionDialogFragment.OnMultiNumberSelectionListener,
    AddEditVillaDialogFragment.DialogListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var appDatabase: AppDatabase
    private lateinit var mainTabsAdapter: MainTabsAdapter
    private val tabsList = mutableListOf<MainTab>()
    private lateinit var whatsappBadge: com.google.android.material.badge.BadgeDrawable
    private lateinit var csvFilePickerLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var contactCsvFilePickerLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestContactCsvPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var balloonSortVillas: Balloon
    private var isSearchModeActive = false
    private var contactsToProcessAfterPermission: List<ContactFromPhone>? = null
    private var activityScopeJob: Job? = null
    private val activityScope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Main + (activityScopeJob ?: Job()))

    /*
    private val requestNotificationListenerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Kullanıcı ayarlar ekranından döndükten sonra iznin verilip verilmediğini tekrar kontrol et
            if (isNotificationServiceEnabled()) {
                showToast("Bildirim erişim izni verildi.")
                updateWhatsAppBadgeVisibility()
            } else {
                showToast("Bildirim erişim izni gerekli.")
            }
        }
    private val notificationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WhatsAppNotificationListener.ACTION_NOTIFICATION_UPDATE) {
                // Bu logu görüyorsan, MainActivity servisten gelen haberi başarıyla almıştır.
                Log.i("MainActivity_WhatsApp", "onReceive: WhatsApp bildirim güncelleme haberi alındı. Rozet güncelleniyor.")
                updateWhatsAppBadgeVisibility()
            }
        }
    }
    */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        Hawk.init(this).build()
        csvFilePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(it, takeFlags)
                } catch (e: SecurityException) {
                    Log.e("MainActivity", "Failed to take persistable URI permission", e)
                }
                Tools(this@MainActivity).processVillaCsvFile(it)
            } ?: run { showToast("Dosya seçilmedi.") }
        }
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // İzin verildi, dosya seçiciyi şimdi gerçekten başlat
                launchCsvFilePicker()
            } else {
                // İzin verilmedi, kullanıcıya bilgi ver
                showToast("Dosya okuma izni verilmedi.")
                // Kullanıcıya neden izne ihtiyacınız olduğunu açıklayan bir diyalog gösterebilirsiniz.
                // Veya ayarlara yönlendirebilirsiniz.
            }
        }
        requestContactCsvPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // İzin verildi, dosya seçiciyi şimdi gerçekten başlat
                launchContactCsvFilePicker()
            } else {
                // İzin verilmedi, kullanıcıya bilgi ver
                showToast("Dosya okuma izni verilmedi.")
                // Kullanıcıya neden izne ihtiyacınız olduğunu açıklayan bir diyalog gösterebilirsiniz.
                // Veya ayarlara yönlendirebilirsiniz.
            }
        }
        window.isNavigationBarContrastEnforced = false
        window.navigationBarColor = getColor(R.color.transparent)
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top + Tools(this).convertDpToPixel(5), 0, 0)
            binding.bottomNavBar.setPadding(Tools(this).convertDpToPixel(16), 0, Tools(this).convertDpToPixel(16), systemBars.bottom)
            insets
        }

        if (Hawk.contains("enable_blur")) setupBlur(Hawk.get<Boolean>("enable_blur"))
        Tools(this).blur(arrayOf(binding.blurFab, binding.blurToolbarButtons, binding.blurMessage, binding.blurNavView, binding.blurSearchbox, binding.blurStatusIndicator), 10f, true)
        if (Hawk.contains("enable_blur")) {
            if (Hawk.get<Boolean>("enable_blur") == false) {
                binding.blurStatusIndicator.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_no_blur)
            } else {
                binding.blurStatusIndicator.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_blur)
            }
        } else {
            binding.blurStatusIndicator.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_blur)
        }

        appDatabase = AppDatabase.Companion.getDatabase(this)
        webSocketClient = (application as SecuAsistApplication).wsClient

        setupWhatsAppBadge() // Rozeti hazırlayan fonksiyonu çağır
        //checkAndRequestNotificationListenerPermission() // İzin kontrolünü yap

        /*
        ContextCompat.registerReceiver(
            this,
            notificationUpdateReceiver,
            IntentFilter(WhatsAppNotificationListener.ACTION_NOTIFICATION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        */

        setupViewPager()
        setupMainTabsRecyclerView()
        observeCargoNotificationStatus()
        setupListeners()
        observeConnectionStatus()
    }

    private fun setupWhatsAppBadge() {
        // WhatsApp butonunuza bir bildirim rozeti oluşturup bağlıyoruz.
        whatsappBadge = com.google.android.material.badge.BadgeDrawable.create(this).apply {
            backgroundColor = ContextCompat.getColor(this@MainActivity, com.google.android.material.R.color.design_default_color_error)
            badgeGravity = com.google.android.material.badge.BadgeDrawable.TOP_END
            // Sayı göstermeyeceğimiz için numarasız bir rozet olacak.
            // İsterseniz number = X diyerek sayı da gösterebilirsiniz.
        }
        // Rozetin görünürlüğünü Hawk'taki son duruma göre ayarla
        updateWhatsAppBadgeVisibility()
    }

    @ExperimentalBadgeUtils
    private fun updateWhatsAppBadgeVisibility() {
        val hasUnread = Hawk.get(WhatsAppNotificationListener.HAWK_KEY_HAS_UNREAD_WHATSAPP, false)
        // Bu log, arayüzü güncelleme fonksiyonunun hangi veriyle çağrıldığını gösterir.
        Log.d("MainActivity_WhatsApp", "updateWhatsAppBadgeVisibility çağrıldı. Okunmamış bildirim var mı?: $hasUnread")
        binding.btnWhatsapp.post {
            if (hasUnread) {
                Log.i("MainActivity_WhatsApp", "Rozet gösteriliyor.")
                BadgeUtils.attachBadgeDrawable(whatsappBadge, binding.btnWhatsapp, binding.root)
            } else {
                Log.i("MainActivity_WhatsApp", "Rozet gizleniyor.")
                BadgeUtils.detachBadgeDrawable(whatsappBadge, binding.btnWhatsapp)
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(this, WhatsAppNotificationListener::class.java)
        return enabledListeners?.contains(componentName.flattenToString()) == true
    }

    private fun checkAndRequestNotificationListenerPermission() {
        if (!isNotificationServiceEnabled()) {
            // Bu log, izin verilmediğini ve kullanıcıya sorulacağını gösterir.
            Log.w("MainActivity_WhatsApp", "checkAndRequest: Bildirim erişim izni verilmemiş. İzin isteme diyaloğu gösteriliyor.")
            AlertDialog.Builder(this)
                .setTitle("Bildirim Erişimi Gerekli")
                .setMessage("WhatsApp bildirimlerini takip edebilmek için uygulamanın bildirimlerinize erişmesine izin vermeniz gerekmektedir.")
                .setPositiveButton("Ayarlara Git") { _, _ ->
                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    //requestNotificationListenerLauncher.launch(intent)
                }
                .setNegativeButton("İptal", null)
                .show()
        } else {
            // Bu log, iznin zaten verilmiş olduğunu ve her şeyin yolunda olması gerektiğini gösterir.
            Log.i("MainActivity_WhatsApp", "checkAndRequest: Bildirim erişim izni zaten verilmiş.")
        }
    }

    private fun observeConnectionStatus() {
        lifecycleScope.launch {
            (application as SecuAsistApplication).wsClient.connectionState.collect { state ->
                updateIndicator(state)
            }
        }
    }

    private fun updateIndicator(status: ConnectionState) {
        when (status) {
            ConnectionState.CONNECTING -> {
                binding.icIndicator.background = AppCompatResources.getDrawable(this, com.serkantken.secuasist.R.drawable.indicator_yellow)
                val blink = AnimationUtils.loadAnimation(this@MainActivity, com.serkantken.secuasist.R.anim.blink_connecting)
                binding.icIndicator.startAnimation(blink)
            }
            ConnectionState.CONNECTED -> {
                binding.icIndicator.background = AppCompatResources.getDrawable(this, com.serkantken.secuasist.R.drawable.indicator_green)
                binding.icIndicator.clearAnimation()

                val balloon: Balloon = createBalloon(this) {
                    setLayout(com.serkantken.secuasist.R.layout.layout_balloon_server_status)
                    setArrowSize(0)
                    setWidth(BalloonSizeSpec.WRAP)
                    setHeight(BalloonSizeSpec.WRAP)
                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.transparent))
                    if (Hawk.contains("less_animations")) {
                        if (Hawk.get<Boolean>("less_animations") == true) {
                            setBalloonAnimation(BalloonAnimation.NONE)
                        } else {
                            setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                        }
                    } else {
                        setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                    }
                    setDismissWhenTouchOutside(true)
                    autoDismissDuration = 5000L
                    setLifecycleOwner(this@MainActivity)
                    build()
                }
                val blur = balloon.getContentView().findViewById<BlurView>(com.serkantken.secuasist.R.id.blur_popup)
                val text = balloon.getContentView().findViewById<TextView>(com.serkantken.secuasist.R.id.tv_server_status)
                Tools(this).blur(arrayOf(blur), 10f, true)
                if (Hawk.contains("enable_blur")) {
                    if (Hawk.get<Boolean>("enable_blur") == false) {
                        blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_no_blur)
                    } else {
                        blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_blur)
                    }
                } else {
                    blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_blur)
                }
                text.text = "Sunucuya bağlandı"

                balloon.setOnBalloonInitializedListener {
                    // 🔹 Küçük bir "parlama" efekti
                    binding.apply {
                        blurStatusIndicator.scaleX = 0.8f
                        blurStatusIndicator.scaleY = 0.8f
                        blurStatusIndicator.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(1000)
                            .start()
                    }
                }

                balloon.showAlignBottom(binding.blurStatusIndicator)
            }
            ConnectionState.DISCONNECTED -> {
                binding.icIndicator.background = AppCompatResources.getDrawable(this, com.serkantken.secuasist.R.drawable.indicator_red)
                binding.icIndicator.clearAnimation()

                val balloon: Balloon = createBalloon(this@MainActivity) {
                    setLayout(com.serkantken.secuasist.R.layout.layout_balloon_server_status)
                    setArrowSize(0)
                    setWidth(BalloonSizeSpec.WRAP)
                    setHeight(BalloonSizeSpec.WRAP)
                    setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.transparent))
                    if (Hawk.contains("less_animations")) {
                        if (Hawk.get<Boolean>("less_animations") == true) {
                            setBalloonAnimation(BalloonAnimation.NONE)
                        } else {
                            setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                        }
                    } else {
                        setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                    }
                    setDismissWhenTouchOutside(true)
                    autoDismissDuration = 5000L
                    setLifecycleOwner(this@MainActivity)
                    build()
                }
                val blur = balloon.getContentView().findViewById<BlurView>(com.serkantken.secuasist.R.id.blur_popup)
                val text = balloon.getContentView().findViewById<TextView>(com.serkantken.secuasist.R.id.tv_server_status)
                Tools(this@MainActivity).blur(arrayOf(blur), 10f, true)
                if (Hawk.contains("enable_blur")) {
                    if (Hawk.get<Boolean>("enable_blur") == false) {
                        blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_no_blur)
                    } else {
                        blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_blur)
                    }
                } else {
                    blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_blur)
                }
                text.text = "Sunucu bağlantısı kesildi"

                balloon.setOnBalloonInitializedListener {
                    // 🔹 Küçük bir "parlama" efekti
                    binding.apply {
                        blurStatusIndicator.scaleX = 0.8f
                        blurStatusIndicator.scaleY = 0.8f
                        blurStatusIndicator.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(1000)
                            .start()
                    }
                }

                balloon.showAlignBottom(binding.blurStatusIndicator)
            }
        }
    }

    private fun setupBlur(lessBlur: Boolean?) {
        if (lessBlur == false) {
            binding.apply {
                blurFab.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_no_blur)
                blurToolbarButtons.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_no_blur)
                blurMessage.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_no_blur)
                blurNavView.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_no_blur)
                blurSearchbox.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_edittext_no_blur)
            }
        } else {
            binding.apply {
                blurFab.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_blur)
                blurToolbarButtons.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_blur)
                blurMessage.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_blur)
                blurNavView.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_blur)
                blurSearchbox.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_edittext)
            }
        }
    }

    data class MainTab(
        val id: Int, // Benzersiz bir kimlik (ViewPager'ın pozisyonuyla eşleşebilir)
        val title: String,
        val iconResId: Int, // Drawable resource ID'si
        var isSelected: Boolean = false,
        var hasNotification: Boolean = false
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
        tabsList.clear()
        tabsList.add(MainTab(0, "Villa Listesi", com.serkantken.secuasist.R.drawable.ic_home, isSelected = true, hasNotification = false))
        tabsList.add(MainTab(1, "Kişi Listesi", com.serkantken.secuasist.R.drawable.ic_person, isSelected = false, hasNotification = false))
        tabsList.add(MainTab(2, "Kargo Yönetimi", com.serkantken.secuasist.R.drawable.ic_cargo, isSelected = false, hasNotification = false))
        tabsList.add(MainTab(3, "Arıza Takibi", com.serkantken.secuasist.R.drawable.ic_videocam, isSelected = false, hasNotification = false))

        mainTabsAdapter = MainTabsAdapter { clickedTab ->
            binding.viewPager.setCurrentItem(clickedTab.id, false)
        }

        binding.rvMainTabs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = mainTabsAdapter
        }
        mainTabsAdapter.submitList(tabsList)
        mainTabsAdapter.selectTab(0)
    }

    private fun observeCargoNotificationStatus() {
        lifecycleScope.launch {
            appDatabase.cargoDao().hasAnyUncalledCargosFlow().collectLatest { hasUncalled ->
                val cargoTabIndex = tabsList.indexOfFirst { it.id == 2 }
                if (cargoTabIndex != -1) {
                    val currentTabState = tabsList[cargoTabIndex]
                    if (currentTabState.hasNotification != hasUncalled) {
                        tabsList[cargoTabIndex] = currentTabState.copy(hasNotification = hasUncalled)
                        mainTabsAdapter.notifyItemChanged(cargoTabIndex)
                        Log.d("MainActivity", "Cargo notification status updated for tab $cargoTabIndex: $hasUncalled")
                    }
                }
            }
        }
    }

    private fun updateMainActionButton(fragmentPosition: Int) {
        val shouldShowFab = when (fragmentPosition) {
            0 -> true
            1 -> true
            2 -> true
            3 -> false
            else -> false
        }

        if (shouldShowFab) {
            when (fragmentPosition) {
                0 -> {
                    binding.icFab.setImageResource(com.serkantken.secuasist.R.drawable.ic_filter)
                    binding.icFab.setOnClickListener { showBalloonSortVillas() }
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
                    binding.icFab.setImageResource(com.serkantken.secuasist.R.drawable.ic_report)
                    binding.icFab.setOnClickListener {
                        val intent = Intent(this, CargoReportActivity::class.java)
                        startActivity(intent)
                    }
                    binding.btnSearch.visibility = View.VISIBLE
                    binding.etSearch.hint = "Kargo şirketi ara"
                }
            }
        }

        val currentVisibility = binding.blurFab.visibility
        val targetVisibility = if (shouldShowFab) View.VISIBLE else View.GONE

        if (currentVisibility != targetVisibility) {
            if (targetVisibility == View.VISIBLE) {
                // ARAMA BUTONUNU GÖSTERME ANİMASYONU
                binding.blurFab.alpha = 0f // Başlangıçta tamamen şeffaf yap

                TransitionManager.beginDelayedTransition(binding.root as ViewGroup,
                    ChangeBounds().setDuration(300))
                binding.blurFab.visibility = View.VISIBLE
                binding.btnSearch.visibility = View.VISIBLE

                binding.blurFab.animate().alpha(1f).setDuration(200).start()

            } else {
                // ARAMA BUTONUNU GİZLEME ANİMASYONU
                binding.blurFab.animate().alpha(0f).setDuration(200)
                    .withEndAction {
                        TransitionManager.beginDelayedTransition(binding.root as ViewGroup,
                            ChangeBounds().setDuration(300))
                        binding.blurFab.visibility = View.GONE
                        binding.btnSearch.visibility = View.GONE
                    }.start()
            }
        } else if (targetVisibility == View.VISIBLE && binding.blurFab.alpha != 1f) {
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
            binding.btnWhatsapp.visibility = View.GONE
            binding.btnMore.visibility = View.GONE
            binding.blurStatusIndicator.visibility = View.GONE
            binding.blurSearchbox.visibility = View.VISIBLE

            // Klavyeyi otomatik aç ve arama kutusuna odaklan
            binding.etSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)

        } else {
            // ARAMA MODUNU KAPAT
            binding.contentTitle.visibility = View.VISIBLE
            binding.btnSearch.visibility = View.VISIBLE
            binding.btnWhatsapp.visibility = View.VISIBLE
            binding.btnMore.visibility = View.VISIBLE
            binding.blurStatusIndicator.visibility = View.VISIBLE
            binding.blurSearchbox.visibility = View.GONE
            binding.btnCancel.visibility = View.GONE

            // Arama kutusunu temizle ve filtreyi sıfırla
            binding.etSearch.text?.clear()
            val currentItem = binding.viewPager.currentItem
            when (currentItem) {
                0 -> {
                    val fragment = supportFragmentManager.findFragmentByTag("f$currentItem") as? VillaFragment
                    fragment?.setSearchQuery("")
                    Log.d("SearchToggle", "VillaFragment search query cleared.")
                }
                1 -> {
                    val fragment = supportFragmentManager.findFragmentByTag("f$currentItem") as? ContactsFragment
                    fragment?.setSearchQuery("")
                    Log.d("SearchToggle", "ContactsFragment search query cleared.")
                }
                2 -> {
                    val fragment = supportFragmentManager.findFragmentByTag("f$currentItem") as? CargoFragment
                    fragment?.setSearchQuery("")
                    Log.d("SearchToggle", "CargoFragment search query cleared.")
                }
            }

            // Klavyeyi gizle
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }
    }

    private fun setupListeners() {
        binding.btnWhatsapp.setOnClickListener {
            val whatsappPackageName = "com.whatsapp"
            try {
               val launchIntent = packageManager.getLaunchIntentForPackage(whatsappPackageName)
               if (launchIntent != null) {
                   startActivity(launchIntent)
               } else {
                   val intent = Intent(Intent.ACTION_VIEW)
                   intent.data = Uri.parse("market://details?id=$whatsappPackageName")
                   startActivity(intent)
                }
            } catch (e: android.content.ActivityNotFoundException) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse("https://play.google.com/store/apps/details?id=$whatsappPackageName")
                    startActivity(intent)
                } catch (webEx: Exception) {
                    showToast("WhatsApp yüklü değil ve uygulama mağazasına ulaşılamadı.")
                }
            } catch (e: Exception) {
                showToast("İşlem gerçekleştirilemedi: ${e.message}")
            }
        }

        binding.btnSearch.setOnClickListener {
            toggleSearchMode(true)
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isSearchModeActive) return

                val query = s.toString()
                if (query.isEmpty()) binding.btnClearBox.visibility = View.GONE else binding.btnClearBox.visibility = View.VISIBLE
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
                }
            }
        })

        binding.btnClearBox.setOnClickListener {
            binding.etSearch.text?.clear()
        }

        binding.btnCancel.setOnClickListener {
            toggleSearchMode(false)
        }

        binding.btnMore.setOnClickListener {
            val balloon: Balloon = Tools(this@MainActivity).createBalloon(
                layout = when (binding.viewPager.currentItem) {
                    0 -> com.serkantken.secuasist.R.layout.layout_balloon_more_options_villa
                    1 -> com.serkantken.secuasist.R.layout.layout_balloon_more_options_contact
                    2 -> com.serkantken.secuasist.R.layout.layout_balloon_more_options_cargo
                    else -> com.serkantken.secuasist.R.layout.layout_balloon_more_options_fault
                },
                isDismissWhenTouchOutside = true,
                isVisibleOverlay = true
            )

            val blur = balloon.getContentView().findViewById<BlurView>(com.serkantken.secuasist.R.id.layout_menu_balloon)
            Tools(this@MainActivity).blur(arrayOf(blur), 10f, true)
            if (Hawk.contains("enable_blur")) {
                if (Hawk.get<Boolean>("enable_blur") == false) {
                    blur.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_balloon_no_blur)
                } else {
                    blur.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_balloon)
                }
            } else {
                blur.background = AppCompatResources.getDrawable(this@MainActivity, com.serkantken.secuasist.R.drawable.background_balloon)
            }
            binding.blurToolbarButtons.visibility = View.INVISIBLE
            binding.blurStatusIndicator.visibility = View.INVISIBLE
            balloon.showAlignTop(binding.blurToolbarButtons, 90, it.height+50)
            balloon.setOnBalloonDismissListener {
                binding.blurToolbarButtons.visibility = View.VISIBLE
                binding.blurStatusIndicator.visibility = View.VISIBLE
            }

            when (binding.viewPager.currentItem) {
                0 -> {
                    val btnNewVilla = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_new_villa)
                    val btnImport = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_import)
                    val btnSettings = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_settings)
                    btnSettings.setOnClickListener {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        balloon.dismiss()
                        finish()
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
                1 -> {
                    val btnImport = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_import)
                    val brnImportFromDevice = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_import_from_device)
                    val btnSettings = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_settings)
                    btnSettings.setOnClickListener {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        balloon.dismiss()
                    }
                    btnImport.setOnClickListener {
                        launchContactCsvFilePicker()
                        balloon.dismiss()
                    }
                    brnImportFromDevice.setOnClickListener {
                        initiateImportContactsFromPhone()
                        balloon.dismiss()
                    }
                }
                2 -> {
                    val btnNewCargoCompany = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_new_cargo_company)
                    val btnSettings = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_settings)
                    btnSettings.setOnClickListener {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        balloon.dismiss()
                    }
                    btnNewCargoCompany.setOnClickListener {
                        showAddEditCargoCompanyDialog(null)
                        balloon.dismiss()
                    }
                }
                else -> {
                    val btnSettings = balloon.getContentView().findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_settings)
                    btnSettings.setOnClickListener {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        balloon.dismiss()
                    }
                }
            }
        }
    }

    fun showBalloonSortVillas() {
        if (binding.viewPager.currentItem != 0) return

        val fadeoutanimation = AnimationUtils.loadAnimation(this, R.anim.fade_out)
        fadeoutanimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                balloonSortVillas = Tools(this@MainActivity).createBalloon(
                    layout = com.serkantken.secuasist.R.layout.layout_balloon_sort_villas,
                    isDismissWhenTouchOutside = true,
                    isVisibleOverlay = true
                )

                val contentView = balloonSortVillas.getContentView()
                val blurView = contentView.findViewById<BlurView>(com.serkantken.secuasist.R.id.layout_menu_balloon)
                Tools(this@MainActivity).blur(arrayOf(blurView), 10f, true)
                if (Hawk.contains("enable_blur")) {
                    if (Hawk.get<Boolean>("enable_blur") == false) {
                        blurView.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_balloon_no_blur)
                    } else {
                        blurView.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_balloon)
                    }
                } else {
                    blurView.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_balloon)
                }

                balloonSortVillas.setOnBalloonDismissListener {
                    val fadeinanimation = AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_in)
                    fadeinanimation.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationEnd(animation: Animation?) {}
                        override fun onAnimationRepeat(animation: Animation?) {}
                        override fun onAnimationStart(animation: Animation?) {
                            binding.bottomNavBar.visibility = View.VISIBLE
                        }
                    })
                    fadeinanimation.duration = 500
                    binding.bottomNavBar.startAnimation(fadeinanimation)
                }

                val villaFragment = supportFragmentManager.findFragmentByTag("f" + binding.viewPager.currentItem) as? VillaFragment

                contentView.findViewById<ConstraintLayout>(com.serkantken.secuasist.R.id.btn_filter_by_street).setOnClickListener {
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
                balloonSortVillas.showAlign(BalloonAlign.BOTTOM,binding.root, listOf(), 500, 0)
            }
            override fun onAnimationEnd(animation: Animation?) {
                binding.bottomNavBar.visibility = View.GONE
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        fadeoutanimation.duration = 250
        binding.bottomNavBar.startAnimation(fadeoutanimation)
    }

    private fun showStreetFilterDialog() {
        val villaFragment = supportFragmentManager.findFragmentByTag("f0") as? VillaFragment ?: return
        val appDatabase = AppDatabase.getDatabase(this)

        lifecycleScope.launch {
            val streetList = appDatabase.villaDao().getUniqueStreetNames().first()

            if (streetList.isEmpty()) {
                showToast("Filtrelenecek sokak bulunamadı.")
                return@launch
            }

            val dialogBinding = DialogFilterByStreetBinding.inflate(layoutInflater)
            Tools(this@MainActivity).blur(arrayOf(dialogBinding.root), 10f, true)
            if (Hawk.contains("enable_blur")) {
                if (Hawk.get<Boolean>("enable_blur") == false) {
                    dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window_no_blur)
                } else {
                    dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
                }
            } else {
                dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
            }

            val alertDialog = AlertDialog.Builder(this@MainActivity)
                .setView(dialogBinding.root)
                .create()

            val streetAdapter = StreetFilterAdapter(streetList) { selectedStreet ->
                villaFragment.setStreetFilter(selectedStreet) // Fragment'a filtreyi uygulat
                showToast("$selectedStreet sokağındaki villalar listeleniyor.")
                alertDialog.dismiss() // Diyalogu kapat
            }

            dialogBinding.rvStreetList.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                adapter = streetAdapter
            }

            dialogBinding.btnClose.setOnClickListener {
                alertDialog.dismiss()
            }

            alertDialog.window?.setBackgroundDrawableResource(R.color.transparent)
            alertDialog.show()
        }
    }

    private fun showStatusFilterDialog() {
        val villaFragment = supportFragmentManager.findFragmentByTag("f0") as? VillaFragment ?: return
        val currentFilters = villaFragment.getCurrentFilterState().activeStatusFilters
        val dialogBinding = DialogFilterByStatusBinding.inflate(layoutInflater)
        Tools(this).blur(arrayOf(dialogBinding.root), 10f, true)
        if (Hawk.contains("enable_blur")) {
            if (Hawk.get<Boolean>("enable_blur") == false) {
                dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window_no_blur)
            } else {
                dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
            }
        } else {
            dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
        }

        dialogBinding.cbFilterUnderConstruction.isChecked = VillaFragment.StatusFilter.UNDER_CONSTRUCTION in currentFilters
        dialogBinding.cbFilterIsSpecial.isChecked = VillaFragment.StatusFilter.IS_SPECIAL in currentFilters
        dialogBinding.cbFilterIsRental.isChecked = VillaFragment.StatusFilter.IS_RENTAL in currentFilters
        dialogBinding.cbFilterNoCargoCalls.isChecked = VillaFragment.StatusFilter.NO_CARGO_CALLS in currentFilters

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        alertDialog.setOnShowListener {
            dialogBinding.btnApply.setOnClickListener {
                val newStatusFilters = mutableSetOf<VillaFragment.StatusFilter>()
                if (dialogBinding.cbFilterUnderConstruction.isChecked) newStatusFilters.add(VillaFragment.StatusFilter.UNDER_CONSTRUCTION)
                if (dialogBinding.cbFilterIsSpecial.isChecked) newStatusFilters.add(VillaFragment.StatusFilter.IS_SPECIAL)
                if (dialogBinding.cbFilterIsRental.isChecked) newStatusFilters.add(VillaFragment.StatusFilter.IS_RENTAL)
                if (dialogBinding.cbFilterNoCargoCalls.isChecked) newStatusFilters.add(VillaFragment.StatusFilter.NO_CARGO_CALLS)

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

    private fun launchCsvFilePicker() {
        val mimeTypes = arrayOf(
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "text/plain"
        )
        try {
            csvFilePickerLauncher.launch(mimeTypes)
        } catch (e: Exception) {
            showToast("Dosya seçici açılamadı.")
            Log.e("MainActivity", "Error launching OpenDocument file picker: ", e)
        }
    }

    private fun launchContactCsvFilePicker() {
        val mimeTypes = arrayOf(
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "text/plain"
        )
        try {
            contactCsvFilePickerLauncher.launch(mimeTypes)
        } catch (e: Exception) {
            showToast("Kişi CSV dosyası seçici açılamadı.")
            Log.e("MainActivity_ContactCSV", "Kişi CSV dosyası seçici başlatılırken hata: ", e)
        }
    }

    override fun onVillaSaved() {
        showToast("Villa başarıyla kaydedildi.")
        // Gerekirse VillaFragment'taki listeyi yenilemek için bir callback çağrılabilir.
        // villaRefreshCallback?.invoke()
    }

    fun showAddEditVillaDialog(villaToEdit: Villa?) {
        val dialogFragment = AddEditVillaDialogFragment.newInstance(villaToEdit)
        dialogFragment.listener = this
        dialogFragment.show(supportFragmentManager, "AddEditVillaDialog")
    }

    fun showVillaInfoBalloon(villa: Villa) {
        val dialogBinding = LayoutBalloonVillaInfoBinding.inflate(layoutInflater)
        Tools(this).blur(arrayOf(dialogBinding.layoutVillaBalloon), 15f, true)
        if (Hawk.contains("enable_blur")) {
            if (Hawk.get<Boolean>("enable_blur") == false) {
                dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_balloon_no_blur)
            } else {
                dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_balloon)
            }
        } else {
            dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_balloon)
        }
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.setCanceledOnTouchOutside(false)

        dialogBinding.apply {
            windowTitle.text = "Villa ${villa.villaNo}"
            if (villa.villaNotes != null) notesText.text = villa.villaNotes else notesText.visibility = View.GONE
            streetTitle.text = villa.villaStreet
            streetTitle.isSelected = true
            if (Hawk.contains("device_location")) {
                if (Hawk.get<String>("device_location") == "A") {
                    chipGateA.isChecked = true
                    navigationText.text = villa.villaNavigationA
                } else {
                    chipGateB.isChecked = true
                    navigationText.text = villa.villaNavigationB
                }
            } else {
                chipGateA.isChecked = true
                navigationText.text = villa.villaNavigationA
            }
            cameraText.text = "Bulunamadı"
            chipGroupNavigation.setOnCheckedChangeListener { chipGroupNavigation, chipId ->
                navigationText.text = when (chipId) {
                    com.serkantken.secuasist.R.id.chip_gate_a -> villa.villaNavigationA
                    com.serkantken.secuasist.R.id.chip_gate_b -> villa.villaNavigationB
                    else -> villa.villaNavigationA
                }
            }
            chipIsEmpty.visibility = if (villa.isVillaEmpty == 1) View.VISIBLE else View.GONE
            chipUnderConstruction.visibility = if (villa.isVillaUnderConstruction == 1) View.VISIBLE else View.GONE
            specialVilla.visibility = if (villa.isVillaSpecial == 1) View.VISIBLE else View.GONE
            rental.visibility = if (villa.isVillaRental == 1) View.VISIBLE else View.GONE
            noCallForCargo.visibility = if (villa.isVillaCallForCargo == 1) View.VISIBLE else View.GONE
            callFromHome.visibility = if (villa.isVillaCallFromHome == 1) View.VISIBLE else View.GONE
            if (villa.villaNotes == null && villa.isVillaEmpty == 0 && villa.isVillaUnderConstruction == 0 && villa.isVillaSpecial == 0 && villa.isVillaRental == 0 && villa.isVillaCallForCargo == 0 && villa.isVillaCallFromHome == 0)
                areaNotes.visibility = View.GONE

            btnClose.setOnClickListener {
                dialog.dismiss()
            }
            btnEditVilla.setOnClickListener {
                showAddEditVillaDialog(villa)
                dialog.dismiss()
            }
            val contactsAdapter = ContactsAdapter(
                this@MainActivity,
                onItemClick = { _, _ -> },
                onCallClick = { contact ->
                    val phoneNumber = contact.contactPhone
                    if (phoneNumber.isNullOrBlank()) {
                        Toast.makeText(this@MainActivity, "Bu kişiye ait telefon numarası bulunamadı.", Toast.LENGTH_SHORT).show()
                        return@ContactsAdapter
                    }

                    lifecycleScope.launch {
                        contact.lastCallTimestamp = System.currentTimeMillis()
                        appDatabase.contactDao().update(contact)
                    }
                    val intent = Intent(Intent.ACTION_CALL)
                    intent.data = "tel:$phoneNumber".toUri()

                    try {
                        startActivity(intent)
                    } catch (e: SecurityException) {
                        Toast.makeText(this@MainActivity, "Telefon arama izni verilmemiş.", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    }
                },
                onDeleteClick = { _ -> },
                isShowingInfo = true,
                isChoosingContact = false
            )
            dialogBinding.rcVillaContacts.layoutManager = LinearLayoutManager(this@MainActivity)
            dialogBinding.rcVillaContacts.adapter = contactsAdapter

            lifecycleScope.launch {
                appDatabase.villaContactDao().getContactsForVilla(villa.villaId).collect { contacts ->
                    if (contacts.isEmpty()) {
                        rcVillaContacts.visibility = View.INVISIBLE
                    } else {
                        rcVillaContacts.visibility = View.VISIBLE
                        contactsAdapter.submitList(contacts)
                    }
                }
            }

            dialog.window?.setBackgroundDrawableResource(R.color.transparent)
            dialog.show()
        }
    }

    lateinit var villaContactsBinding : DialogManageVillaContactsBinding
    fun showManageVillaContactsDialog(villa: Villa) {
        villaContactsBinding = DialogManageVillaContactsBinding.inflate(layoutInflater)
        Tools(this).blur(arrayOf(villaContactsBinding.blur), 10f, true)
        if (Hawk.contains("enable_blur")) {
            if (Hawk.get<Boolean>("enable_blur") == false) {
                villaContactsBinding.blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window_no_blur)
            } else {
                villaContactsBinding.blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
            }
        } else {
            villaContactsBinding.blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
        }
        val dialog = AlertDialog.Builder(this).setView(villaContactsBinding.root).create()

        lateinit var draggableAdapter: DraggableContactsAdapter

        draggableAdapter = DraggableContactsAdapter { contact ->
            AlertDialog.Builder(this@MainActivity)
                .setTitle("İlişkiyi Sil")
                .setMessage("Villa ${villa.villaNo} için ${contact.contactName} adlı kişinin ilişkisini silmek istediğinize emin misiniz?")
                .setPositiveButton("Evet, Sil") { _, _ ->
                    activityScope.launch(Dispatchers.IO) {
                        appDatabase.villaContactDao().deleteByVillaIdAndContactId(villa.villaId, contact.contactId)
                        (application as SecuAsistApplication).sendUnlink(villa.villaId, contact.contactId)

                        val currentList = draggableAdapter.currentList.toMutableList()
                        currentList.remove(contact)

                        launch(Dispatchers.Main) {
                            draggableAdapter.submitList(currentList)
                            showToast("${contact.contactName} ilişkisi silindi.")
                        }

                        val associationsCount = appDatabase.villaContactDao().getVillaAssociationsCount(contact.contactId)
                        if (associationsCount == 0) {
                            launch(Dispatchers.Main) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Kişiyi Sistemden Sil")
                                    .setMessage("${contact.contactName} adlı kişinin başka villa ile ilişkisi kalmadı. Bu kişiyi sistemden tamamen silmek ister misiniz?")
                                    .setPositiveButton("Evet, Tamamen Sil") { _, _ ->
                                        activityScope.launch(Dispatchers.IO) {
                                            appDatabase.contactDao().delete(contact)
                                            (application as SecuAsistApplication).sendDelete("CONTACT", contact.contactId)
                                            launch(Dispatchers.Main) { showToast("${contact.contactName} sistemden tamamen silindi.") }
                                        }
                                    }
                                    .setNegativeButton("Hayır, Kişiyi Tut", null)
                                    .show()
                            }
                        }
                    }
                }
                .setNegativeButton("İptal", null)
                .show()
        }

        villaContactsBinding.recyclerViewVillaContacts.adapter = draggableAdapter
        villaContactsBinding.recyclerViewVillaContacts.layoutManager = LinearLayoutManager(this)

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                draggableAdapter.onRowMoved(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(villaContactsBinding.recyclerViewVillaContacts)

        lifecycleScope.launch {
            appDatabase.villaContactDao().getContactsForVilla(villa.villaId).collect { contacts ->
                if (contacts.isEmpty()) {
                    villaContactsBinding.layoutEmptyList.visibility = View.VISIBLE
                } else {
                    draggableAdapter.submitList(contacts)
                }
            }
        }

        villaContactsBinding.btnClose.setOnClickListener {
            activityScope.launch(Dispatchers.IO) {
                val finalList = draggableAdapter.getFinalList()
                val existingRelations = appDatabase.villaContactDao().getVillaContactRelations(villa.villaId)
                val updatedRelations = mutableListOf<VillaContact>()
                finalList.forEachIndexed { newIndex, contact ->
                    val relationToUpdate = existingRelations.find { it.contactId == contact.contactId }
                    relationToUpdate?.let {
                        it.orderIndex = newIndex
                        updatedRelations.add(it)
                    }
                }
                if (updatedRelations.isNotEmpty()) {
                    appDatabase.villaContactDao().updateVillaContacts(updatedRelations)
                }
            }
            dialog.dismiss()
        }

        villaContactsBinding.btnAddContactToVilla.setOnClickListener {
            it.visibility = View.INVISIBLE
            val balloon = createBalloon(this) {
                setLayout(com.serkantken.secuasist.R.layout.layout_balloon_new_villa_contact)
                setArrowSize(0)
                setWidth(BalloonSizeSpec.WRAP)
                setHeight(BalloonSizeSpec.WRAP)
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.transparent))
                if (Hawk.contains("less_animations")) {
                    if (Hawk.get<Boolean>("less_animations") == true) {
                        setBalloonAnimation(BalloonAnimation.NONE)
                    } else {
                        setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                    }
                } else {
                    setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                }
                setIsVisibleOverlay(true)
                setOverlayShape(BalloonOverlayRoundRect(12f, 12f))
                setLifecycleOwner(this@MainActivity)
                build()
            }
            balloon.showAlignTop(it, 50, it.height+10)
            val blur: BlurView = balloon.getContentView().findViewById(com.serkantken.secuasist.R.id.layout_menu_balloon)
            Tools(this@MainActivity).blur(arrayOf(blur), 10f, true)
            if (Hawk.contains("enable_blur")) {
                if (Hawk.get<Boolean>("enable_blur") == false) {
                    blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window_no_blur)
                } else {
                    blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
                }
            } else {
                blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
            }
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
        }

        dialog.window?.setBackgroundDrawableResource(R.color.transparent)
        dialog.show()
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
            proceedWithNewContactCreationDialog(contactToEdit, villaIdForAssociation, isEditMode)
        }
    }

    private fun linkContactToVilla(contact: Contact, villaId: Int, onComplete: () -> Unit) {
        activityScope.launch(Dispatchers.IO) {
            try {
                // Önce bu ilişkinin veritabanında zaten var olup olmadığını kontrol et
                val existingRelation = appDatabase.villaContactDao().getVillaContact(villaId, contact.contactId, "Diğer")

                if (existingRelation == null) {
                    // Yeni bir ilişki nesnesi oluştur. 'orderIndex' için o villadaki mevcut kişi sayısını kullan.
                    val newOrderIndex = appDatabase.villaContactDao().getVillaContactRelations(villaId).size
                    val newVillaRelation = VillaContact(
                        villaId = villaId,
                        contactId = contact.contactId,
                        contactType = "Diğer", // Varsayılan bir tip
                        isRealOwner = false, // Varsayılan
                        notes = null,
                        orderIndex = newOrderIndex
                    )

                    // 1. Yerel veritabanına ekle
                    appDatabase.villaContactDao().insert(newVillaRelation)
                    Log.i("MainActivityContacts", "Linked contact ${contact.contactId} to villa $villaId locally.")

                    // 2. Sunucuya senkronize et
                    (application as SecuAsistApplication).sendUpsert(newVillaRelation)
                    Log.i("MainActivityContacts", "Sent link-relation to server for contact ${contact.contactId} and villa $villaId.")

                } else {
                    Log.i("MainActivityContacts", "Contact ${contact.contactId} is already linked to villa $villaId. Skipping.")
                }
            } catch (e: Exception) {
                Log.e("MainActivityContacts", "Error in linkContactToVilla for contact ${contact.contactId} and villa $villaId: ${e.message}", e)
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    private fun showManageCompanyDeliverersDialog(company: CargoCompany) {
        val dialogBinding = DialogManageCompanyDeliverersBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvDialogTitle.text = "${company.companyName} - Kuryeleri Yönet"

        val assignedDeliverersAdapter = AssignedDeliverersAdapter { contactToRemove ->
            // Dağıtıcıyı şirketten kaldır
            lifecycleScope.launch {
                appDatabase.companyDelivererDao().removeDelivererFromCompany(company.companyId, contactToRemove.contactId)
                // İsteğe bağlı: kullanıcıya geri bildirim ver (Toast vb.)
            }
        }

        dialogBinding.recyclerViewCompanyContacts.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = assignedDeliverersAdapter
        }

        // Şirkete atanmış dağıtıcıları yükle ve adapter'a ver
        lifecycleScope.launch {
            appDatabase.companyDelivererDao().getDeliverersForCompanyFlow(company.companyId)
                .collectLatest { deliverers ->
                    assignedDeliverersAdapter.submitList(deliverers.sortedBy { it.contactName }) // Alfabetik sırala
                    if (deliverers.isEmpty()) {
                        dialogBinding.layoutEmptyList.visibility = View.VISIBLE
                    } else {
                        dialogBinding.layoutEmptyList.visibility = View.GONE
                    }
                }
        }

        dialogBinding.btnAddContactToCompany.setOnClickListener {
            it.visibility = View.INVISIBLE
            val balloon = createBalloon(this) {
                setLayout(com.serkantken.secuasist.R.layout.layout_balloon_new_villa_contact)
                setArrowSize(0)
                setWidth(BalloonSizeSpec.WRAP)
                setHeight(BalloonSizeSpec.WRAP)
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.transparent))
                if (Hawk.contains("less_animations")) {
                    if (Hawk.get<Boolean>("less_animations") == true) {
                        setBalloonAnimation(BalloonAnimation.NONE)
                    } else {
                        setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                    }
                } else {
                    setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                }
                setIsVisibleOverlay(true)
                setOverlayShape(BalloonOverlayRoundRect(12f, 12f))
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
                //showAddEditContactDialog(null) { newContactId ->
                    // Yeni kişi başarıyla oluşturulduysa, bu şirkete dağıtıcı olarak ata
                    //lifecycleScope.launch {
                        //val crossRef = CompanyDelivererCrossRef(companyId = company.companyId, contactId = newContactId)
                        //appDatabase.companyDelivererDao().addDelivererToCompany(crossRef)
                        // İsteğe bağlı: kullanıcıya geri bildirim ve listenin güncellenmesi
                        // (Flow otomatik güncelleyecektir)
                    //}
                //}
                balloon.dismiss()
            }
            existingContacts.setOnClickListener {
                showSelectContactsToAssignDialog(company)
                balloon.dismiss()
            }
            // Yeni kişi oluşturma ve atama dialogunu göster

        }

        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSelectContactsToAssignDialog(company: CargoCompany) {
        // Bu metod, o şirkete henüz atanmamış tüm kişileri listeler
        // ve seçilenlerin şirkete atanmasını sağlar.
        // Bu metodun implementasyonu bir sonraki adımda yapılacak.
        // Şimdilik bir Toast mesajı gösterelim.
        Toast.makeText(this, "Mevcut kişilerden seçme özelliği eklenecek.", Toast.LENGTH_SHORT).show()
    }

    private fun showExistingContactsSelectionDialog(villaIdToAssociate: Int) {
        val dialogBinding = DialogSelectContactBinding.inflate(layoutInflater)
        Tools(this).blur(arrayOf(dialogBinding.blur), 15f, true)
        if (Hawk.contains("enable_blur")) {
            if (Hawk.get<Boolean>("enable_blur") == false) {
                dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window_no_blur)
            } else {
                dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
            }
        } else {
            dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
        }
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        var allContacts = listOf<Contact>()

        val contactsAdapter = ContactsAdapter(
            this@MainActivity,
            onItemClick = { selectedContact, _ ->
                linkContactToVilla(selectedContact, villaIdToAssociate) {
                    villaContactsBinding.layoutEmptyList.visibility = View.GONE
                    dialog.dismiss()
                }
            },
            onCallClick = { _ -> /* Bu diyalogda arama işlevi yok */ },
            onDeleteClick = { _ -> /* Bu diyalogda silme işlevi yok */ },
            isShowingInfo = false,
            isChoosingContact = true
        )

        dialogBinding.rvContacts.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = contactsAdapter
        }

        dialogBinding.etSearchContact.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase().trim()
                val filteredList = if (query.isBlank()) {
                    allContacts
                } else {
                    allContacts.filter {
                        it.contactName?.lowercase()?.contains(query) == true || it.contactPhone?.contains(query) == true
                    }
                }
                contactsAdapter.submitList(filteredList)
            }
        })

        lifecycleScope.launch(Dispatchers.IO) {
            val contactsFromDb = appDatabase.contactDao().getAllContactsAsList()
            allContacts = contactsFromDb

            launch(Dispatchers.Main) {
                contactsAdapter.submitList(allContacts)
            }
        }

        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(R.color.transparent)
        dialog.show()
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
        if (Hawk.contains("enable_blur")) {
            if (Hawk.get<Boolean>("enable_blur") == false) {
                dialogBinding.blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window_no_blur)
            } else {
                dialogBinding.blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
            }
        } else {
            dialogBinding.blur.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
        }

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
                    if (Hawk.contains("less_animations")) {
                        if (Hawk.get<Boolean>("less_animations") == true) {
                            setBalloonAnimation(BalloonAnimation.NONE)
                        } else {
                            setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                        }
                    } else {
                        setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                    }
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
                        (application as SecuAsistApplication).sendUpsert(updatedContact)
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
                            (application as SecuAsistApplication).sendUpsert(createdContact)
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
                val villaContact = VillaContact(
                    villaId = villaId,
                    contactId = contact.contactId,
                    isRealOwner = false,
                    contactType = "Diğer",
                    notes = null
                )
                appDatabase.villaContactDao().insert(villaContact)
                showToast("${contact.contactName} başarıyla Villa $villaId için eklendi.")
                (application as SecuAsistApplication).sendUpsert(villaContact)
            }
        }
    }

    internal fun showAddEditCargoCompanyDialog(companyToEdit: CargoCompany?) {
        val dialogBinding = DialogAddEditCargoCompanyBinding.inflate(layoutInflater)
        val isEditMode = companyToEdit != null
        Tools(this).blur(arrayOf(dialogBinding.blurWindow), 10f, true)
        if (Hawk.contains("enable_blur")) {
            if (Hawk.get<Boolean>("enable_blur") == false) {
                dialogBinding.blurWindow.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window_no_blur)
            } else {
                dialogBinding.blurWindow.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
            }
        } else {
            dialogBinding.blurWindow.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
        }

        if (isEditMode) {
            dialogBinding.windowTitle.text = "Kargo Şirketini Düzenle"
            dialogBinding.etCompanyName.setText(companyToEdit?.companyName)
        } else {
            dialogBinding.windowTitle.text = "Yeni Kargo Şirketi Ekle"
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        alertDialog.window?.setBackgroundDrawableResource(R.color.transparent)

        dialogBinding.btnSave.setOnClickListener {
            val companyName = dialogBinding.etCompanyName.text.toString().trim()

            if (companyName.isEmpty()) {
                showToast("Kargo şirketi adı boş olamaz.")
                return@setOnClickListener
            }

            activityScope.launch {
                try {
                    if (isEditMode) {
                        val updatedCompany = companyToEdit.copy(companyName = companyName)
                        appDatabase.cargoCompanyDao().update(updatedCompany) // cargoDao'da update fonksiyonunuz olmalı
                        showToast("${updatedCompany.companyName} güncellendi.")
                        (application as SecuAsistApplication).sendUpsert(updatedCompany)
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
                            (application as SecuAsistApplication).sendUpsert(newCompanyWithId)
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
        dialogBinding.btnCompanyContacts.setOnClickListener {
            showToast("Bu özellik yakında eklenecek.")
        }

        alertDialog.show()
    }

    private val requestContactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivityContacts", "READ_CONTACTS permission granted.")
                // İzin verildi, asıl kişi çekme işlemini başlat (viewModel scope veya lifecycleScope içinde)
                lifecycleScope.launch {
                    showContactsImportProgress(true) // İlerleme göstergesini başlat
                    val (imported, skipped, multiNumber) = importContactsFromPhone() // Asıl fonksiyon
                    showContactsImportProgress(false) // İlerleme göstergesini bitir
                    showImportSummary(imported, skipped, multiNumber) // Sonuçları göster
                    if (multiNumber.isNotEmpty()) {
                        showMultiNumberSelectionDialogIfNeeded(multiNumber)
                        Log.d("MainActivityContacts", "${multiNumber.size} contacts with multiple numbers need selection.")
                        // showMultiNumberSelectionDialog(multiNumber)
                    }
                }
            } else {
                Log.d("MainActivityContacts", "READ_CONTACTS permission denied.")
                Toast.makeText(this, "Rehber erişim izni verilmedi.", Toast.LENGTH_SHORT).show()
            }
        }

    fun initiateImportContactsFromPhone() { // Bu fonksiyonu menüdeki seçeneğe bağlayın
        Log.d("MainActivityContacts", "initiateImportContactsFromPhone called.")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivityContacts", "READ_CONTACTS permission already granted. Starting import.")
            // İzin zaten var, asıl kişi çekme işlemini başlat
            lifecycleScope.launch {
                showContactsImportProgress(true)
                val (imported, skipped, multiNumber) = importContactsFromPhone()
                showContactsImportProgress(false)
                showImportSummary(imported, skipped, multiNumber)
                if (multiNumber.isNotEmpty()) {
                    showMultiNumberSelectionDialogIfNeeded(multiNumber)
                    Log.d("MainActivityContacts", "${multiNumber.size} contacts with multiple numbers need selection.")// showMultiNumberSelectionDialog(multiNumber)
                }
            }
        } else {
            Log.d("MainActivityContacts", "READ_CONTACTS permission not granted. Requesting permission.")
            // İzin iste
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    @SuppressLint("Range")
    private suspend fun importContactsFromPhone(): Triple<Int, Int, List<ContactFromPhone>> = withContext(Dispatchers.IO) {
        var successfullyInsertedNewContactCount = 0
        var successfullyLinkedToVillaAttemptCount = 0 // Yapılan linkleme denemesi sayısı
        var skippedAsDuplicateInContactsTableCount = 0
        val contactsWithMultipleNumbersToResolve = mutableListOf<ContactFromPhone>()

        // Bu liste, DB'ye eklenecek Contact nesnesini ve ilişkilendirileceği Villa ID'lerinin listesini tutacak.
        val processedContactsForDb = mutableListOf<Pair<com.serkantken.secuasist.models.Contact, List<Int>>>()

        val contentResolver = this@MainActivity.contentResolver
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )

        // Regex: Satır başında "100" veya "100 - 200" veya "100 - 200 - 300" gibi kalıpları arar
        // ve geri kalan ismi yakalar.
        val potentialVillaPartRegex = "^([0-9]+(?:\\s*-\\s*[0-9]+)*)\\s*(.*)$".toRegex()

        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        )

        cursor?.use { contactsCursor ->
            Log.d("MainActivityContacts", "Total contacts in phonebook (approx): ${contactsCursor.count}")
            if (contactsCursor.moveToFirst()) {
                var iteratedContacts = 0
                do {
                    iteratedContacts++
                    val phoneContactId = contactsCursor.getString(contactsCursor.getColumnIndex(ContactsContract.Contacts._ID))
                    var displayName = contactsCursor.getString(contactsCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                    Log.d("MainActivityContacts", "Processing contact $iteratedContacts: ID '$phoneContactId', Original Name '$displayName'")

                    if (displayName.isNullOrBlank()) {
                        Log.w("MainActivityContacts", "Skipping contact ID '$phoneContactId': Name is null or blank.")
                        continue
                    }

                    val currentContactFromPhoneHelper = ContactFromPhone(phoneContactId, displayName)

                    // Telefon numaralarını al
                    val phoneProjection = arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE
                    )
                    val phoneCursorInner = contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        phoneProjection,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf(phoneContactId),
                        null
                    )

                    phoneCursorInner?.use { pCursor ->
                        if (pCursor.moveToFirst()) {
                            do {
                                val phoneNumberRaw = pCursor.getString(pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                val phoneType = pCursor.getInt(pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE))
                                if (!phoneNumberRaw.isNullOrBlank()) {
                                    val cleanedPhoneNumber = phoneNumberRaw.replace("\\s".toRegex(), "") // Boşlukları temizle
                                    val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                        this@MainActivity.resources, phoneType, "Diğer"
                                    ).toString()
                                    currentContactFromPhoneHelper.phoneOptions.add(PhoneOptionForContact(cleanedPhoneNumber, typeLabel))
                                }
                            } while (pCursor.moveToNext())
                        }
                    }

                    if (currentContactFromPhoneHelper.phoneOptions.isEmpty()) {
                        Log.w("MainActivityContacts", "Skipping contact '${displayName}': No phone numbers.")
                        continue
                    }

                    if (currentContactFromPhoneHelper.phoneOptions.size == 1) {
                        val selectedPhoneNumber = currentContactFromPhoneHelper.phoneOptions.first().number // Zaten temizlenmiş olmalı

                        val existingContact = appDatabase.contactDao().getContactByPhoneNumber(selectedPhoneNumber)
                        if (existingContact != null) {
                            Log.d("MainActivityContacts", "Contact '${displayName}' with phone '$selectedPhoneNumber' already exists in Contacts table. Skipping insertion.")
                            skippedAsDuplicateInContactsTableCount++
                            continue
                        }

                        val detectedVillaIdsForCurrentContact = mutableListOf<Int>()

                        val matchResult = potentialVillaPartRegex.find(displayName.trim())
                        if (matchResult != null) {
                            val villaNumbersPartString = matchResult.groups[1]?.value ?: "" // "100 - 205" veya "300"
                            val restOfNameFromString = matchResult.groups[2]?.value?.trim() ?: "" // "Ali Veli" veya ""

                            val individualVillaNumbersFromString = villaNumbersPartString.split("-")
                                .mapNotNull { it.trim().toIntOrNull() }

                            if (individualVillaNumbersFromString.isNotEmpty()) {
                                var allVillasInNameExistInDB = true
                                val tempVillaIdsFromName = mutableListOf<Int>()
                                for (villaNo in individualVillaNumbersFromString) {
                                    try {
                                        val foundVilla = appDatabase.villaDao().getVillaByNo(villaNo)
                                        if (foundVilla != null) {
                                            tempVillaIdsFromName.add(foundVilla.villaId)
                                        } else {
                                            allVillasInNameExistInDB = false
                                            Log.d("MainActivityContacts", "VillaNo '$villaNo' from name '${displayName}' not found in DB.")
                                            break
                                        }
                                    } catch (e: Exception) {
                                        allVillasInNameExistInDB = false
                                        Log.e("MainActivityContacts", "DB Error for villaNo $villaNo from name '${displayName}': ${e.message}")
                                        break
                                    }
                                }

                                if (allVillasInNameExistInDB && tempVillaIdsFromName.isNotEmpty()) {
                                    detectedVillaIdsForCurrentContact.addAll(tempVillaIdsFromName)
                                } else {
                                    // Eğer villa numaralarından herhangi biri bulunamazsa veya bir hata oluşursa,
                                    // villa ataması yapma ve ismi orijinal bırak.
                                    Log.d("MainActivityContacts", "Not all villa numbers found or error for '${displayName}'. Using original name. Villa IDs cleared for auto-assign.")
                                    // detectedVillaIdsForCurrentContact boş kalacak, nameToUseInContactObject orijinal displayName olacak
                                }
                            }
                        }
                        // else: İsim, villa numarası kalıbıyla başlamıyorsa, detectedVillaIdsForCurrentContact boş kalacak.

                        val newAppContact = Contact(
                            contactName = displayName,
                            contactPhone = selectedPhoneNumber,
                            lastCallTimestamp = null
                        )
                        processedContactsForDb.add(Pair(newAppContact, detectedVillaIdsForCurrentContact.toList()))

                    } else { // Birden fazla numarası var
                        var anyNumberExistsInDB = false
                        for(phoneOption in currentContactFromPhoneHelper.phoneOptions) {
                            if (appDatabase.contactDao().getContactByPhoneNumber(phoneOption.number) != null) {
                                anyNumberExistsInDB = true
                                Log.d("MainActivityContacts", "Contact '${displayName}' (multi-num) has phone '${phoneOption.number}' which already exists. Skipping multi-select consideration for now.")
                                break
                            }
                        }
                        if (!anyNumberExistsInDB) {
                            contactsWithMultipleNumbersToResolve.add(currentContactFromPhoneHelper)
                        } else {
                            skippedAsDuplicateInContactsTableCount++
                        }
                    }
                } while (contactsCursor.moveToNext())
                Log.d("MainActivityContacts", "Finished iterating phonebook contacts. Total iterated: $iteratedContacts")
            } else {
                Log.d("MainActivityContacts", "Contacts cursor is empty or moveToFirst() failed.")
            }
        } // cursor.use

        // 1. Aşama: Tüm yeni (yinelenen olmayan) Contact nesnelerini veritabanına ekle
        if (processedContactsForDb.isNotEmpty()) {
            val contactsOnlyToInsert = processedContactsForDb.map { it.first }
            try {
                // Room'un insertAll metodu, eklenen satırların ID'lerini bir liste olarak döndürür.
                val insertedIds = appDatabase.contactDao().insertAll(contactsOnlyToInsert)
                successfullyInsertedNewContactCount = insertedIds.size
                Log.d("MainActivityContacts", "Successfully inserted $successfullyInsertedNewContactCount new contacts into Local DB.")

                // YENİ VE KRİTİK KISIM: Eklenen kişileri sunucuya gönder
                insertedIds.forEachIndexed { index, newId ->
                    // Orijinal nesneyi alıp, yeni ID'si ile bir kopyasını oluştur.
                    val originalContactData = contactsOnlyToInsert[index]
                    val contactToSend = originalContactData.copy(contactId = newId.toInt())
                    (application as SecuAsistApplication).sendUpsert(contactToSend)
                    Log.d("MainActivityContacts", "Syncing new contact to server: ${contactToSend.contactName}")
                    delay(50)
                }
            } catch (e: Exception) {
                Log.e("MainActivityContacts", "Error inserting contacts to DB: ${e.message}", e)
                // Hata durumunda, başarılı sayısını ve sonraki adımları gözden geçirmek gerekebilir.
                // Şimdilik, successfullyInsertedNewContactCount'u 0'a çekelim ki linkleme yapılmasın.
                successfullyInsertedNewContactCount = 0
            }
        }

        // 2. Aşama: Başarıyla eklenen kişileri, tespit edilen villalarla ilişkilendir
        if (successfullyInsertedNewContactCount > 0) {
            Log.d("MainActivityContacts", "Attempting to link ${processedContactsForDb.size} processed contacts to their respective villas.")
            for ((contactDataFromPair, villaIdListFromPair) in processedContactsForDb) {
                if (villaIdListFromPair.isNotEmpty()) {
                    // DB'ye yeni eklenen Contact nesnesini (artık contactId'si var) telefon numarasıyla çek
                    val contactFromDb = appDatabase.contactDao().getContactByPhoneNumber(contactDataFromPair.contactPhone!!)
                    if (contactFromDb != null) {
                        if (villaIdListFromPair.isNotEmpty()) {
                            for (villaIdToLink in villaIdListFromPair) {
                                linkContactToVilla(contactFromDb, villaIdToLink) {
                                    Log.d("MainActivityContacts", "Villa linking process callback for contact ${contactFromDb.contactId} to villa $villaIdToLink.")
                                }
                                successfullyLinkedToVillaAttemptCount++
                            }
                        }
                    } else {
                        Log.w("MainActivityContacts", "Could not re-fetch contact with phone ${contactDataFromPair.contactPhone} for villa linking. This contact might not have been inserted correctly or was part of a failed batch insert.")
                    }
                }
            }
            Log.d("MainActivityContacts", "Finished attempting to link contacts. Successfully initiated links for: $successfullyLinkedToVillaAttemptCount potential relations.")
        }

        Log.d("MainActivityContacts", "Import process finished. New Contacts Inserted: $successfullyInsertedNewContactCount, Link Attempts to Villa: $successfullyLinkedToVillaAttemptCount, Skipped Duplicates: $skippedAsDuplicateInContactsTableCount, Multi-Number Pending: ${contactsWithMultipleNumbersToResolve.size}")
        return@withContext Triple(successfullyInsertedNewContactCount, skippedAsDuplicateInContactsTableCount, contactsWithMultipleNumbersToResolve)
    }

    private var progressDialog: AlertDialog? = null
    private lateinit var layoutDialogLoadingBinding: LayoutDialogLoadingBinding

    private fun showContactsImportProgress(show: Boolean) {
        if (show) {
            if (progressDialog == null) {
                layoutDialogLoadingBinding = LayoutDialogLoadingBinding.inflate(layoutInflater)
                progressDialog = AlertDialog.Builder(this)
                    .setView(layoutDialogLoadingBinding.root)
                    .setCancelable(false)
                    .create()
            }
            Tools(this@MainActivity).blur(arrayOf(layoutDialogLoadingBinding.blurPopup), 10f, true)
            if (Hawk.contains("enable_blur")) {
                if (Hawk.get<Boolean>("enable_blur") == false) {
                    layoutDialogLoadingBinding.blurPopup.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window_no_blur)
                } else {
                    layoutDialogLoadingBinding.blurPopup.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
                }
            } else {
                layoutDialogLoadingBinding.blurPopup.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
            }
            layoutDialogLoadingBinding.titleLoading.text = "Lütfen Bekleyin"
            layoutDialogLoadingBinding.labelLoading.text = "Rehber okunuyor..."
            progressDialog?.window?.setBackgroundDrawableResource(R.color.transparent)
            progressDialog?.show()
        } else {
            progressDialog?.dismiss()
            progressDialog = null
            layoutDialogLoadingBinding.titleLoading.text = ""
            layoutDialogLoadingBinding.labelLoading.text = ""
        }
    }

    private fun showImportSummary(imported: Int, skipped: Int, multiNumber: List<ContactFromPhone>) {
        val message = "İçe aktarma tamamlandı.\nBaşarıyla eklendi: $imported\nTekrar eden kayıt (atlandı): $skipped" +
                if (multiNumber.isNotEmpty()) "\nNumara seçimi bekleyen: ${multiNumber.size}" else ""

        AlertDialog.Builder(this)
            .setTitle("İçe Aktarma Sonucu")
            .setMessage(message)
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun showMultiNumberSelectionDialogIfNeeded(contactsForSelection: List<ContactFromPhone>) {
        if (contactsForSelection.isNotEmpty()) {
            val dialogFragment = MultiNumberSelectionDialogFragment.newInstance(contactsForSelection)
            dialogFragment.setOnMultiNumberSelectionListener(this) // Listener'ı set et
            dialogFragment.show(supportFragmentManager, "MultiNumberSelectionDialog")
        }
    }

    // OnMultiNumberSelectionListener metodlarını override et
    override fun onSelectionsCompleted(selectedContacts: Map<String, String>) {
        Log.d("MainActivityContacts", "Multi-number selections completed. Map size: ${selectedContacts.size}")
        if (selectedContacts.isEmpty()) return

        lifecycleScope.launch {
            processSelectedMultiNumberContacts(selectedContacts)
        }
    }

    override fun onSelectionCancelled() {
        Log.d("MainActivityContacts", "Multi-number selection was cancelled.")
        Toast.makeText(this, "Numara seçimi iptal edildi.", Toast.LENGTH_SHORT).show()
    }

    private suspend fun processSelectedMultiNumberContacts(selectedMap: Map<String, String>) = withContext(Dispatchers.IO) {
        var newContactsAdded = 0
        var newLinksToVilla = 0
        val originalMultiNumberContacts = contactsToProcessAfterPermission ?: emptyList() // İzin sonrası saklanan liste

        Log.d("MainActivityContacts", "Processing ${selectedMap.size} selections from multi-number dialog.")

        for ((phoneContactId, selectedPhoneNumber) in selectedMap) {
            val originalContactInfo = originalMultiNumberContacts.find { it.phoneContactId == phoneContactId }
            if (originalContactInfo == null) {
                Log.w("MainActivityContacts", "Original contact info not found for phoneContactId: $phoneContactId. Skipping.")
                continue
            }

            val displayName = originalContactInfo.displayName

            // Yinelenen kontrolü (Contacts tablosunda bu seçilen numara var mı?)
            val existingContact = appDatabase.contactDao().getContactByPhoneNumber(selectedPhoneNumber.replace("\\s".toRegex(), ""))
            if (existingContact != null) {
                Log.d("MainActivityContacts", "Selected number '$selectedPhoneNumber' for '${displayName}' already exists. Skipping.")
                continue
            }

            var determinedVillaId: Int? = null
            var finalContactName = displayName
            val nameParts = displayName.trim().split(" ", limit = 2)
            if (nameParts.isNotEmpty()) {
                val potentialVillaNo = nameParts[0].toIntOrNull()
                if (potentialVillaNo != null) {
                    try {
                        val foundVilla = appDatabase.villaDao().getVillaByNo(potentialVillaNo)
                        if (foundVilla != null) {
                            determinedVillaId = foundVilla.villaId
                            finalContactName = if (nameParts.size > 1 && nameParts[1].isNotBlank()) nameParts[1] else displayName
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivityContacts", "DB Error for villaNo $potentialVillaNo for '${displayName}': ${e.message}")
                    }
                }
            }

            val newAppContact = Contact(
                contactName = finalContactName,
                contactPhone = selectedPhoneNumber.replace("\\s".toRegex(), ""),
                lastCallTimestamp = null
            )

            try {
                val insertedContactId = appDatabase.contactDao().insert(newAppContact) // Tek bir contact ekleme metodu
                if (insertedContactId > 0) {
                    newContactsAdded++
                    if (determinedVillaId != null) {
                        // Yeni contactId'yi kullanarak linkleme yap.
                        // insertContact'ın yeni ID'yi döndürdüğünü veya
                        // yeni kişiyi telefonla tekrar çekmemiz gerektiğini varsayıyoruz.
                        // En iyisi, insertContact'ın eklenen Contact nesnesini veya ID'sini döndürmesidir.
                        // Şimdilik, telefonla tekrar çekelim:
                        val contactJustAdded = appDatabase.contactDao().getContactByPhoneNumber(newAppContact.contactPhone!!)
                        if (contactJustAdded != null) {
                            linkContactToVilla(contactJustAdded, determinedVillaId) {
                                Log.d("MainActivityContacts", "Async link complete for multi-select contact.")
                            }
                            newLinksToVilla++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivityContacts", "Error inserting multi-select contact '${displayName}': ${e.message}")
            }
        }

        Log.d("MainActivityContacts", "Finished processing multi-number selections. New contacts: $newContactsAdded, New links: $newLinksToVilla")
        // Kullanıcıya özet göster
        runOnUiThread { // UI thread'e geç
            Toast.makeText(this@MainActivity, "$newContactsAdded kişi daha eklendi (numara seçiminden).", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        activityScopeJob = activityScope.launch {
            webSocketClient.incomingMessages.collect { message ->
                try {
                    val json = JSONObject(message)
                    val action = json.optString("type")
                    val status = json.optString("status")
                    val origin = json.optString("origin")

                    when (action) {
                        "STATUS" -> {
                            val msg = json.optJSONObject("payload")?.optString("message") ?: "Sunucuya bağlandı."
                            showToast(msg)
                        }

                        // 🏠 Villa işlemleri
                        "VILLA_UPSERT", "VILLA_DELETE" -> {
                            val villaName = json.optJSONObject("payload")?.optString("villaNo") ?: "Bilinmeyen villa"
                            val where = if (origin == "self") "bu cihazdan" else "sunucudan"
                            val operation = if (action == "VILLA_UPSERT") "güncellendi/eklendi" else "silindi"
                            showToast("🏠 Villa $villaName $where $operation")
                        }

                        // 👤 Kişi (Contact) işlemleri
                        "CONTACT_UPSERT", "CONTACT_DELETE" -> {
                            val contactName = json.optJSONObject("payload")?.optString("contactName") ?: "Bilinmeyen kişi"
                            val operation = if (action == "CONTACT_UPSERT") "Güncellendi/Eklendi" else "Silindi"
                            showToast("👤 $contactName $operation")
                        }

                        // 🔄 Senkronizasyon vb.
                        "SYNC_COMPLETE" -> {
                            showToast("🔄 Senkronizasyon tamamlandı")
                        }

                        else -> {
                            // JSON olarak geldi ama tanımsız bir işlemse
                            showToast("🔹 Sunucu bildirimi: $message")
                        }
                    }

                } catch (e: JSONException) {
                    // JSON değilse, eski STATUS formatıdır
                    if (message.startsWith("STATUS:")) {
                        when {
                            message.startsWith("STATUS:CONNECTED:") ->
                                showToast("Sunucuya bağlandı.")
                            message.startsWith("STATUS:DISCONNECTED:") ->
                                showToast("Sunucu bağlantısı kesildi. Yeniden bağlanıyor...")
                            message.startsWith("STATUS:DISCONNECTING:") ->
                                showToast("Sunucu bağlantısı kapanıyor...")
                            message.startsWith("STATUS:ERROR:") ->
                                showToast("Sunucuya bağlantı hatası oluştu. Yeniden bağlanıyor...")
                            else ->
                                showToast("Sunucu Durum: $message")
                        }
                    } else {
                        showToast("Sunucudan gelen mesaj: $message")
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        activityScopeJob?.cancel()
        activityScopeJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        //unregisterReceiver(notificationUpdateReceiver)
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

    fun showBalloon(view: View, message: String, location: Int) {
        val balloon = createBalloon(this) {
            setText(message)
            setArrowSize(5)
            setWidthRatio(0.5f)
            setHeight(BalloonSizeSpec.WRAP)
            setTextSize(15f)
            autoDismissDuration = 3000
            if (Hawk.contains("less_animations")) {
                if (Hawk.get<Boolean>("less_animations") == true) {
                    setBalloonAnimation(BalloonAnimation.NONE)
                } else {
                    setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                }
            } else {
                setBalloonAnimation(BalloonAnimation.OVERSHOOT)
            }
            build()
        }
        when (location) {
            0 -> balloon.showAlignTop(view)
            1 -> balloon.showAlignEnd(view)
            2 -> balloon.showAlignBottom(view)
            3 -> balloon.showAlignStart(view)
        }
    }
}
