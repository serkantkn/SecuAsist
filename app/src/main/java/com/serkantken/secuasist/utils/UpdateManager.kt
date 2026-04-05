package com.serkantken.secuasist.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.serkantken.secuasist.BuildConfig
import com.serkantken.secuasist.models.GitHubRelease
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.io.File
import java.io.IOException

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val repoUrl = "https://api.github.com/repos/serkantkn/SecuAsist/releases"

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable = _isUpdateAvailable.asStateFlow()

    private val _latestVersionInfo = MutableStateFlow<Triple<String, String, String>?>(null) // version, notes, downloadUrl
    val latestVersionInfo = _latestVersionInfo.asStateFlow()

    fun checkForUpdates() {
        val request = Request.Builder()
            .url(repoUrl)
            .header("User-Agent", "SecuAsist-App") // GitHub API requires a User-Agent
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                showToast("Güncelleme kontrolü başarısız: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    showToast("Güncelleme hatası: ${response.code}")
                    return
                }
                val body = response.body?.string() ?: return
                try {
                    val releases = gson.fromJson(body, Array<GitHubRelease>::class.java)
                    val release = releases.firstOrNull()
                    if (release == null) {
                        return
                    }
                    
                    // Fallback to name if tag_name doesn't look like a standard version
                    val latestVersionStr = if (release.tag_name?.contains(".") == true) release.tag_name else release.name
                    val latestVersion = latestVersionStr?.removePrefix("v") ?: ""
                    val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v")
                    
                    cleanupOldApks(currentVersion)

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        val apkAsset = release.assets?.firstOrNull { it.name?.endsWith(".apk") == true }
                        if (apkAsset != null) {
                            _isUpdateAvailable.value = true
                            _latestVersionInfo.value = Triple(latestVersion, release.body ?: "", apkAsset.browser_download_url!!)
                            
                            val file = File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                "SecuAsist-v$latestVersion.apk"
                            )
                            promptUpdateDialog(latestVersion, release.body, file, apkAsset.browser_download_url!!)
                        } else {
                            showToast("GitHub'da APK dosyası bulunamadı.")
                        }
                    } else {
                        // Uygulamanın güncel olduğunu bildir
                        showToast("Uygulama güncel ($currentVersion)")
                    }
                } catch (e: Exception) {
                    showToast("Sürüm çözümlenemedi: ${e.message}")
                    e.printStackTrace()
                }
            }
        })
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        // Extract all numeric sequences
        val currentNumbers = Regex("\\d+").findAll(current).map { it.value.toInt() }.toList()
        val latestNumbers = Regex("\\d+").findAll(latest).map { it.value.toInt() }.toList()
        
        val length = maxOf(currentNumbers.size, latestNumbers.size)
        for (i in 0 until length) {
            val curr = currentNumbers.getOrNull(i) ?: 0
            val late = latestNumbers.getOrNull(i) ?: 0
            if (late > curr) return true
            if (curr > late) return false
        }
        return false
    }

    private fun cleanupOldApks(currentVersion: String) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (dir?.exists() == true && dir.isDirectory) {
            val files = dir.listFiles { _, name -> name.startsWith("SecuAsist-v") && name.endsWith(".apk") }
            files?.forEach { file ->
                val versionInName = file.name.removePrefix("SecuAsist-v").removeSuffix(".apk")
                if (!isNewerVersion(currentVersion, versionInName)) {
                    try {
                        file.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun startDownload(url: String, version: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("SecuAsist Güncellemesi")
            .setDescription("Sürüm v$version indiriliyor...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SecuAsist-v$version.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SecuAsist-v$version.apk")
                    verifyAndInstall(version, file)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        
        showToast("Güncelleme bulundu, indiriliyor...")
    }

    private fun installApk(version: String) {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SecuAsist-v$version.apk"
        )
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun promptUpdateDialog(latestVersion: String, releaseNotes: String?, file: File, downloadUrl: String) {
        val activity = context as? android.app.Activity
        if (activity == null || activity.isFinishing) return

        activity.runOnUiThread {
            val baseMessage = "Uygulamanın yeni bir sürümü mevcut."
            val finalMessage = if (!releaseNotes.isNullOrBlank()) {
                "$baseMessage\n\nSürüm Notları:\n$releaseNotes\n\nİndirip kurmak istiyor musunuz?"
            } else {
                "$baseMessage İndirip kurmak istiyor musunuz?"
            }

            android.app.AlertDialog.Builder(activity)
                .setTitle("Yeni Güncelleme: v$latestVersion")
                .setMessage(finalMessage)
                .setPositiveButton("İndir / Kur") { _, _ ->
                    if (file.exists()) {
                        showToast("Güncelleme dosyası bulundu, imzalar kontrol ediliyor...")
                        verifyAndInstall(latestVersion, file)
                    } else {
                        startDownload(downloadUrl, latestVersion)
                    }
                }
                .setNegativeButton("Daha Sonra", null)
                .show()
        }
    }

    private fun verifyAndInstall(version: String, file: File) {
        if (!file.exists()) {
            showToast("Hata: İndirilen APK bulunamadı.")
            return
        }

        if (verifySignature(file)) {
            installApk(version)
        } else {
            showToast("Güvenlik Uyarısı: Güncelleme dosyasının imzası orijinal uygulama ile eşleşmiyor! İndirilen dosya silindi.")
            try {
                file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun verifySignature(apkFile: File): Boolean {
        try {
            val pm = context.packageManager
            
            val installedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            } else {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
            }
            
            val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                android.content.pm.PackageManager.GET_SIGNATURES
            }
            
            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, archiveFlags)
            
            val downloadedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo?.signingInfo?.apkContentsSigners
            } else {
                archiveInfo?.signatures
            }
            
            if (installedSignatures.isNullOrEmpty() || downloadedSignatures.isNullOrEmpty()) {
                return false
            }
            
            val currentHash = installedSignatures[0].toByteArray().contentHashCode()
            val downloadedHash = downloadedSignatures[0].toByteArray().contentHashCode()
            
            return currentHash == downloadedHash
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun showToast(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
