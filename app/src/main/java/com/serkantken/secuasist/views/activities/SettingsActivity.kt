package com.serkantken.secuasist.views.activities

import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.R
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.ActivitySettingsBinding
import com.serkantken.secuasist.utils.Tools

class SettingsActivity : AppCompatActivity() {
    private var _binding: ActivitySettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var appDatabase: AppDatabase

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
        Tools(this).blur(arrayOf(binding.blurBack), 10f, true)
        binding.blurBack.setOnClickListener {
            Hawk.put("device_name", binding.etDeviceName.text.toString().trim())
            Hawk.put("device_location", if (binding.chipGroupNavigation.checkedChipId == R.id.chip_gate_a) "A" else "B")
            finish()
        }
        binding.etIPAddress.text = Editable.Factory.getInstance().newEditable(Hawk.get("server_ip", ""))
        binding.btnConnect.setOnClickListener {
            val ipAddress = binding.etIPAddress.text.toString().trim()
            if (ipAddress.isNotEmpty()) {
                if (android.net.InetAddresses.isNumericAddress(ipAddress)) { // API 29+ için
                    val oldIp = Hawk.get("server_ip", "")
                    Hawk.put("server_ip", ipAddress)
                    Toast.makeText(this, "Sunucu IP adresi kaydedildi.", Toast.LENGTH_SHORT).show()

                    if (oldIp != ipAddress) {
                        val app = application as SecuAsistApplication
                        app.webSocketClient.updateIpAndReconnect(ipAddress)
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

        binding.switchLessAnimations.isChecked = Hawk.contains("less_animations") && Hawk.get("less_animations", false)
        binding.switchLessBlur.isChecked = Hawk.contains("less_blur") && Hawk.get("less_blur", true)
        binding.switchLessAnimations.setOnCheckedChangeListener { _, isChecked ->
            Hawk.put("less_animations", isChecked)
            Toast.makeText(this, "Animasyonlar ${if (isChecked) "kapalı" else "açık"} olarak ayarlandı.", Toast.LENGTH_SHORT).show()
            recreate()
        }
        binding.switchLessBlur.setOnCheckedChangeListener { _, isChecked ->
            Hawk.put("less_blur", isChecked)
            Toast.makeText(this, "Cam efekti ${if (isChecked) "kapalı" else "açık"} olarak ayarlandı.", Toast.LENGTH_SHORT).show()
        }
    }
}