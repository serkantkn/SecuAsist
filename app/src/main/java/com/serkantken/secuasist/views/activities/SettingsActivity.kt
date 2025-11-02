package com.serkantken.secuasist.views.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.R
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.ActivitySettingsBinding
import com.serkantken.secuasist.network.ConnectionState
import com.serkantken.secuasist.utils.Tools
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.createBalloon
import eightbitlab.com.blurview.BlurView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {
    private var _binding: ActivitySettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var appDatabase: AppDatabase

    @OptIn(ExperimentalBadgeUtils::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivitySettingsBinding.inflate(layoutInflater)
        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        window.isNavigationBarContrastEnforced = false
        window.navigationBarColor = getColor(android.R.color.transparent)
        appDatabase = AppDatabase.getDatabase(this@SettingsActivity)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            binding.scrollView.setPadding(0, systemBars.top + Tools(this).convertDpToPixel(55), 0, systemBars.bottom + Tools(this).convertDpToPixel(20))
            insets
        }
        Hawk.init(this).build()
        Tools(this).blur(arrayOf(binding.blurBack, binding.blurToolbarButtons), 10f, true)
        binding.switchLessAnimations.isChecked = if (Hawk.contains("less_animations")) Hawk.get("less_animations") else false
        binding.switchBlur.isChecked = if (Hawk.contains("enable_blur")) Hawk.get("enable_blur") else true
        binding.etDeviceName.text = Editable.Factory.getInstance().newEditable(Hawk.get("device_name", ""))
        binding.chipGroupNavigation.check(if (Hawk.get("device_location", "A") == "A") R.id.chip_gate_a else R.id.chip_gate_b)
        binding.etIPAddress.text = Editable.Factory.getInstance().newEditable(Hawk.get("server_ip", ""))

        binding.blurBack.setOnClickListener { backOperation() }
        onBackPressedDispatcher.addCallback(this) { backOperation() }

        binding.btnAndroidSettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Ayarlar açılamadı.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnConnect.setOnClickListener {
            val ipAddress = binding.etIPAddress.text.toString().trim()
            if (ipAddress.isNotEmpty()) {
                if (android.net.InetAddresses.isNumericAddress(ipAddress)) { // API 29+ için
                    val oldIp = Hawk.get("server_ip", "")
                    Hawk.put("server_ip", ipAddress)
                    Toast.makeText(this, "Sunucu IP adresi kaydedildi. Uygulamayı kapatıp yeniden açmanız gerekebilir.", Toast.LENGTH_SHORT).show()

                    if (oldIp != ipAddress) {
                        (application as SecuAsistApplication).wsClient.reconnectWithNewIp(ipAddress)
                        Log.d("SettingsActivity", "WebSocket'e yeni IP ($ipAddress) ile yeniden bağlanma isteği gönderildi.")
                    } else {
                        Log.d("SettingsActivity", "IP adresi değişmedi. Yeniden bağlanmaya gerek yok.")
                    }
                } else {
                    binding.etIPAddress.error = "Geçerli bir IP adresi girin."
                }
            } else {
                binding.etIPAddress.error = "IP adresi boş olamaz."
            }
        }

        binding.btnSync.setOnClickListener {
            val deviceId = Hawk.get("device_name", "unknown")
            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                put("timestamp", System.currentTimeMillis())
            }
            val message = JSONObject().apply {
                put("type", "SYNC_REQUEST")
                put("payload", payload)
            }.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // webSocketClient.sendMessage(message) — senin implementasyona göre bu metodu çağır
                    (application as SecuAsistApplication).wsClient.sendMessage(message)

                    // opsiyonel: kısa geri bildirim (ana thread'te)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Senkronizasyon isteği gönderildi.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.labelSyncStatus.text = "Senkronizasyon isteği gönderilemedi"
                        Toast.makeText(this@SettingsActivity, "Gönderim hatası: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.switchLessAnimations.setOnCheckedChangeListener { _, isChecked ->
            Hawk.put("less_animations", isChecked)
            Toast.makeText(this, "Animasyonlar ${if (isChecked) "kapalı" else "açık"} olarak ayarlandı.", Toast.LENGTH_SHORT).show()
            recreate()
        }
        binding.switchBlur.setOnCheckedChangeListener { _, isChecked ->
            Hawk.put("enable_blur", isChecked)
            Toast.makeText(this, "Bulanıklık efekti ${if (isChecked) "açık" else "kapalı"} olarak ayarlandı.", Toast.LENGTH_SHORT).show()
            recreate()
        }

        observeConnectionStatus()
    }

    private fun observeConnectionStatus() {
        lifecycleScope.launch {
            (application as SecuAsistApplication).wsClient.connectionState.collect { state ->
                updateIndicator(state)
            }
        }
    }

    private fun updateIndicator(state: ConnectionState) {
        when (state) {
            ConnectionState.CONNECTING -> {
                binding.indicatorServerStatus.background = AppCompatResources.getDrawable(this, R.drawable.indicator_yellow)
                val blink = AnimationUtils.loadAnimation(this, R.anim.blink_connecting)
                binding.indicatorServerStatus.startAnimation(blink)
                binding.labelServerStatus.text = "Bağlanıyor"
            }
            ConnectionState.CONNECTED -> {
                binding.indicatorServerStatus.background = AppCompatResources.getDrawable(this, R.drawable.indicator_green)
                binding.labelServerStatus.text = "Bağlandı"
                binding.indicatorServerStatus.clearAnimation()
            }
            ConnectionState.DISCONNECTED -> {
                binding.indicatorServerStatus.background = AppCompatResources.getDrawable(this, R.drawable.indicator_red)
                binding.labelServerStatus.text = "Bağlantı kesildi"
                binding.indicatorServerStatus.clearAnimation()
            }
        }
    }

    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }

        val resolveInfo = packageManager.resolveActivity(intent, 0)
        val defaultPackage = resolveInfo?.activityInfo?.packageName

        return defaultPackage == packageName
    }

    private val switchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            // Kullanıcıya launcher seçtir
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } else {
            // Varsayılan launcher seçimini sıfırlaması için sistem ayarlarını açtır
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                startActivity(fallbackIntent)
            }
        }
    }

    @OptIn(ExperimentalBadgeUtils::class)
    private fun backOperation() {
        Hawk.put("device_name", binding.etDeviceName.text.toString().trim())
        Hawk.put("device_location", if (binding.chipGroupNavigation.checkedChipId == R.id.chip_gate_a) "A" else "B")
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()

        val isLauncher = isDefaultLauncher()
        binding.switchSetLauncher.setOnCheckedChangeListener(null) // Listener’ı geçici kapat
        binding.switchSetLauncher.isChecked = isLauncher           // UI’yı güncelle
        binding.switchSetLauncher.setOnCheckedChangeListener(switchListener) // Tekrar bağla
    }
}