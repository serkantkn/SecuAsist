package com.serkantken.secuasist.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val villas: List<Villa> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val villaContacts: List<VillaContact> = emptyList(),
    val companies: List<CargoCompany> = emptyList(),
    val cargos: List<Cargo> = emptyList(),
    val cameras: List<Camera> = emptyList(),
    val intercoms: List<Intercom> = emptyList(),
    val companyDeliverers: List<CompanyDelivererCrossRef> = emptyList(),
    val cameraVisibleVillas: List<CameraVisibleVillaCrossRef> = emptyList()
)

class BackupManager(private val context: Context) {
    private val app = context.applicationContext as SecuAsistApplication
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportToUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val db = app.db

            val backupData = BackupData(
                villas = db.villaDao().getAllVillasSync(),
                contacts = db.contactDao().getAllContactsSync(),
                villaContacts = db.villaContactDao().getAllSync(),
                companies = db.cargoCompanyDao().getAllSync(),
                cargos = db.cargoDao().getAllSync(),
                cameras = db.cameraDao().getAllSync(),
                intercoms = db.intercomDao().getAllSync(),
                companyDeliverers = db.companyDelivererDao().getAllSync(),
                cameraVisibleVillas = db.cameraDao().getAllCrossRefsSync()
            )

            val json = gson.toJson(backupData)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
            }

            val totalRecords = backupData.villas.size + backupData.contacts.size +
                    backupData.villaContacts.size + backupData.companies.size +
                    backupData.cargos.size + backupData.cameras.size +
                    backupData.intercoms.size

            Log.i("BackupManager", "✅ Yedekleme tamamlandı: $totalRecords kayıt")
            Result.success(totalRecords)
        } catch (e: Exception) {
            Log.e("BackupManager", "❌ Yedekleme hatası: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun importFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: throw Exception("Dosya okunamadı")

            val backupData = gson.fromJson(json, BackupData::class.java)
            val db = app.db

            db.withTransaction {
                // Önce tüm referans tabloları temizle
                db.companyDelivererDao().deleteAll()
                db.villaContactDao().deleteAll()
                db.cameraDao().deleteAllCrossRefs()
                db.cargoDao().deleteAll()
                db.intercomDao().deleteAll()
                db.cameraDao().deleteAll()
                db.cargoCompanyDao().deleteAll()
                db.contactDao().deleteAll()
                db.villaDao().deleteAll()

                // Sırayla yükle
                if (backupData.villas.isNotEmpty()) db.villaDao().insertAll(backupData.villas)
                if (backupData.contacts.isNotEmpty()) db.contactDao().insertAll(backupData.contacts)
                if (backupData.villaContacts.isNotEmpty()) db.villaContactDao().insertAll(backupData.villaContacts)
                if (backupData.companies.isNotEmpty()) db.cargoCompanyDao().insertAll(backupData.companies)
                if (backupData.cargos.isNotEmpty()) db.cargoDao().insertAll(backupData.cargos)
                if (backupData.cameras.isNotEmpty()) db.cameraDao().insertAll(backupData.cameras)
                if (backupData.intercoms.isNotEmpty()) db.intercomDao().insertAll(backupData.intercoms)
                if (backupData.companyDeliverers.isNotEmpty()) db.companyDelivererDao().insertAll(backupData.companyDeliverers)
                if (backupData.cameraVisibleVillas.isNotEmpty()) db.cameraDao().insertAllCrossRefs(backupData.cameraVisibleVillas)
            }

            val totalRecords = backupData.villas.size + backupData.contacts.size +
                    backupData.villaContacts.size + backupData.companies.size +
                    backupData.cargos.size + backupData.cameras.size +
                    backupData.intercoms.size

            Log.i("BackupManager", "✅ Geri yükleme tamamlandı: $totalRecords kayıt")
            Result.success(totalRecords)
        } catch (e: Exception) {
            Log.e("BackupManager", "❌ Geri yükleme hatası: ${e.message}", e)
            Result.failure(e)
        }
    }
}
