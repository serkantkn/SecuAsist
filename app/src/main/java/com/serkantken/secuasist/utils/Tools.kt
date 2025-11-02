package com.serkantken.secuasist.utils

import android.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewOutlineProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.orhanobut.hawk.Hawk
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.database.AppDatabase
import com.serkantken.secuasist.databinding.DialogAlertBinding
import com.serkantken.secuasist.models.Villa
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.createBalloon
import com.skydoves.balloon.overlay.BalloonOverlayCircle
import com.skydoves.balloon.overlay.BalloonOverlayShape
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderScriptBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class Tools(private val activity: Activity) {

    fun blur(views: Array<BlurView>, radius: Float, isRounded: Boolean) {
        for (view in views) {
            val decorView: View = activity.window.decorView
            val rootView = decorView.findViewById<ViewGroup>(R.id.content)
            val windowBackground = decorView.background
            Hawk.init(activity).build()

            view.setupWith(rootView, RenderScriptBlur(activity))
                .setFrameClearDrawable(windowBackground)
                .setBlurRadius(radius)
                .setBlurAutoUpdate(true)
            if (Hawk.contains("enable_blur")) {
                if (Hawk.get<Boolean>("enable_blur") == true) {
                    view.setBlurEnabled(true)
                } else {
                    view.setBlurEnabled(false)
                }
            } else {
                view.setBlurEnabled(true)
            }
            if (isRounded) {
                view.outlineProvider = ViewOutlineProvider.BACKGROUND
                view.clipToOutline = true
            }
        }
    }

    fun convertDpToPixel(dp: Int): Int {
        val density: Float = activity.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    fun createBalloon(
        layout: Int,
        isDismissWhenTouchOutside: Boolean,
        isVisibleOverlay: Boolean
    ): Balloon
    {
        Hawk.init(activity).build()
        return createBalloon(activity) {
            setLayout(layout)
            setArrowSize(0)
            setWidth(BalloonSizeSpec.WRAP)
            setHeight(BalloonSizeSpec.WRAP)
            setBackgroundColor(ContextCompat.getColor(activity, R.color.transparent))
            if (Hawk.contains("less_animations")) {
                if (Hawk.get<Boolean>("less_animations") == true) {
                    setBalloonAnimation(BalloonAnimation.NONE)
                } else {
                    setBalloonAnimation(BalloonAnimation.OVERSHOOT)
                }
            } else {
                setBalloonAnimation(BalloonAnimation.OVERSHOOT)
            }
            setDismissWhenTouchOutside(isDismissWhenTouchOutside)
            setIsVisibleOverlay(isVisibleOverlay)
            if (isVisibleOverlay) {
                setOverlayShape(BalloonOverlayCircle(12f))
                overlayColor = ContextCompat.getColor(
                    activity,
                    com.serkantken.secuasist.R.color.black_transparent
                )
            }
            setLifecycleOwner(activity as LifecycleOwner?)
            build()
        }
    }

    fun processVillaCsvFile(fileUri: Uri) {
        Log.d("MainActivity_CSV", "processCsvFile çağrıldı. URI: $fileUri")
        val appDatabase = AppDatabase.getDatabase(activity)

        (activity as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) {
            val villasToInsert = mutableListOf<Villa>()
            val villasToUpdate = mutableListOf<Villa>()
            var hataliSatirSayisi = 0
            var eklenenVillaSayisi = 0
            var guncellenenVillaSayisi = 0

            try {
                activity.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                        var isFirstLine = true
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            var currentLine = line!!

                            if (isFirstLine) {
                                if (currentLine.startsWith("\uFEFF")) {
                                    currentLine = currentLine.substring(1)
                                    Log.d("MainActivity_CSV", "UTF-8 BOM karakteri bulundu ve temizlendi.")
                                }
                                isFirstLine = false
                            }

                            if (currentLine.isBlank()) {
                                continue
                            }

                            try {
                                val columns = currentLine.split(";(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                                    .map { it.trim().removeSurrounding("\"") }

                                val villaNoStr = columns.getOrNull(0)
                                val villaNo = villaNoStr?.toIntOrNull()
                                    ?: throw IllegalArgumentException("VillaNo Hatalı/Eksik: '$villaNoStr'")

                                val csvVillaNotes = columns.getOrNull(1)
                                val csvVillaStreet = columns.getOrNull(2)
                                val csvVillaNavigationA = columns.getOrNull(3)
                                val csvVillaNavigationB = columns.getOrNull(4)
                                val csvIsVillaUnderConstruction = columns.getOrNull(5)?.toIntOrNull() ?: 0
                                val csvIsVillaSpecial = columns.getOrNull(6)?.toIntOrNull() ?: 0
                                val csvIsVillaRental = columns.getOrNull(7)?.toIntOrNull() ?: 0
                                val csvIsVillaCallFromHome = columns.getOrNull(8)?.toIntOrNull() ?: 0
                                val csvIsVillaCallForCargo = columns.getOrNull(9)?.toIntOrNull() ?: 0
                                val csvIsVillaEmpty = columns.getOrNull(10)?.toIntOrNull() ?: 0

                                val existingVilla = appDatabase.villaDao().getVillaByNo(villaNo)

                                if (existingVilla != null) {
                                    existingVilla.villaNotes = csvVillaNotes
                                    existingVilla.villaStreet = csvVillaStreet
                                    existingVilla.villaNavigationA = csvVillaNavigationA
                                    existingVilla.villaNavigationB = csvVillaNavigationB
                                    existingVilla.isVillaUnderConstruction = csvIsVillaUnderConstruction
                                    existingVilla.isVillaSpecial = csvIsVillaSpecial
                                    existingVilla.isVillaRental = csvIsVillaRental
                                    existingVilla.isVillaCallFromHome = csvIsVillaCallFromHome
                                    existingVilla.isVillaCallForCargo = csvIsVillaCallForCargo
                                    existingVilla.isVillaEmpty = csvIsVillaEmpty
                                    villasToUpdate.add(existingVilla)
                                } else {
                                    val newVilla = Villa(
                                        villaNo = villaNo,
                                        villaNotes = csvVillaNotes,
                                        villaStreet = csvVillaStreet,
                                        villaNavigationA = csvVillaNavigationA,
                                        villaNavigationB = csvVillaNavigationB,
                                        isVillaUnderConstruction = csvIsVillaUnderConstruction,
                                        isVillaSpecial = csvIsVillaSpecial,
                                        isVillaRental = csvIsVillaRental,
                                        isVillaCallFromHome = csvIsVillaCallFromHome,
                                        isVillaCallForCargo = csvIsVillaCallForCargo,
                                        isVillaEmpty = csvIsVillaEmpty
                                    )
                                    villasToInsert.add(newVilla)
                                }
                            } catch (e: Exception) {
                                hataliSatirSayisi++
                                Log.e("MainActivity_CSV", "Satır işlenirken Hata: '$line'. Hata: ${e.message}", e)
                            }
                        }
                    }
                } ?: throw Exception("Dosya akışı açılamadı.")

                // --- VERİTABANI VE SENKRONİZASYON İŞLEMLERİ ---

                // 1. Yeni villaları ekle ve sunucuya gönder
                if (villasToInsert.isNotEmpty()) {
                    Log.d("MainActivity_CSV", "${villasToInsert.size} yeni villa eklenecek ve senkronize edilecek.")
                    villasToInsert.forEach { villaToInsert ->
                        // Yerel DB'ye ekle ve Room tarafından üretilen yeni ID'yi al
                        val newId = appDatabase.villaDao().insert(villaToInsert)

                        // Sunucuya göndermek için ID'si atanmış bir kopya oluştur
                        val villaToSend = villaToInsert.copy(villaId = newId.toInt())

                        // Sunucuya gönder
                        (activity.application as SecuAsistApplication).sendUpsert(villaToSend)
                    }
                    eklenenVillaSayisi = villasToInsert.size
                }

                // 2. Mevcut villaları güncelle ve sunucuya gönder
                if (villasToUpdate.isNotEmpty()) {
                    Log.d("MainActivity_CSV", "${villasToUpdate.size} mevcut villa güncellenecek ve senkronize edilecek.")
                    villasToUpdate.forEach { villaToUpdate ->
                        // Yerel DB'de güncelle
                        appDatabase.villaDao().update(villaToUpdate)

                        // Sunucuya gönder
                        (activity.application as SecuAsistApplication).sendUpsert(villaToUpdate)
                    }
                    guncellenenVillaSayisi = villasToUpdate.size
                }

                // 3. Kullanıcıyı bilgilendir
                launch(Dispatchers.Main) {
                    // loadingDialog.dismiss()
                    var message = ""
                    if (eklenenVillaSayisi > 0) message += "$eklenenVillaSayisi yeni villa eklendi. "
                    if (guncellenenVillaSayisi > 0) message += "$guncellenenVillaSayisi mevcut villa güncellendi. "
                    if (hataliSatirSayisi > 0) message += "$hataliSatirSayisi satır hatalıydı."
                    if (message.isBlank() && hataliSatirSayisi == 0) message = "İşlenecek yeni veya güncellenecek villa bulunamadı."

                    createAlertDialog("İşlem tamamlandı",message.trim())
                    Log.d("MainActivity_CSV", "Villa CSV İşlem sonucu: ${message.trim()}")
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    createAlertDialog("İşlem tamamlandı","Dosya işlenirken bir hata oluştu: ${e.message}")
                    Log.e("MainActivity_CSV", "CSV dosyası işlenirken genel hata", e)
                }
            }
        }
    }

    fun createAlertDialog(title: String, text: String) {
        val dialogBinding = DialogAlertBinding.inflate(activity.layoutInflater)
        val alertDialog = androidx.appcompat.app.AlertDialog.Builder(activity)
            .setView(dialogBinding.root)
            .create()

        blur(arrayOf(dialogBinding.blurPopup), 10f, true)
        if (Hawk.contains("enable_blur")) {
            if (Hawk.get<Boolean>("enable_blur") == false) {
                dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window_no_blur)
            } else {
                dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
            }
        } else {
            dialogBinding.root.setBackgroundResource(com.serkantken.secuasist.R.drawable.background_window)
        }

        dialogBinding.title.text = title
        dialogBinding.label.text = text

        dialogBinding.btnOK.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.window?.setBackgroundDrawableResource(R.color.transparent)
        alertDialog.show()
    }

    fun doSizingAnimation(direction: Int, isExpanded: Boolean, containerView: View, packageView: View, completion: () -> Unit) {
        if (direction == 0) {
            val initialWidth = containerView.width
            packageView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(containerView.height, View.MeasureSpec.EXACTLY)
            )
            val targetWidth: Int
            if (isExpanded) {
                targetWidth = initialWidth - packageView.measuredWidth
                packageView.visibility = View.GONE
                completion()
            } else
                targetWidth = initialWidth + packageView.measuredWidth

            val animator = ValueAnimator.ofInt(initialWidth, targetWidth)
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                val layoutParams = containerView.layoutParams
                layoutParams.width = value
                containerView.layoutParams = layoutParams
            }
            if (!isExpanded) {
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        packageView.visibility = View.VISIBLE
                        val layoutParams = containerView.layoutParams
                        layoutParams.height = LayoutParams.WRAP_CONTENT
                        containerView.layoutParams = layoutParams
                        completion()
                    }
                })
            }
            animator.duration = 300
            animator.start()
        } else if (direction == 1) {
            val initialHeight = containerView.height
            packageView.measure(
                View.MeasureSpec.makeMeasureSpec(containerView.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val targetWidth: Int
            if (isExpanded) {
                targetWidth = initialHeight - packageView.measuredHeight
                packageView.visibility = View.GONE
                completion()
            } else
                targetWidth = initialHeight + packageView.measuredHeight

            val animator = ValueAnimator.ofInt(initialHeight, targetWidth)
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                val layoutParams = containerView.layoutParams
                layoutParams.height = value
                containerView.layoutParams = layoutParams
            }
            if (!isExpanded) {
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        packageView.visibility = View.VISIBLE
                        val layoutParams = containerView.layoutParams
                        layoutParams.height = LayoutParams.WRAP_CONTENT
                        containerView.layoutParams = layoutParams
                        completion()
                    }
                })
            }
            animator.duration = 300
            animator.start()
        }
    }
}