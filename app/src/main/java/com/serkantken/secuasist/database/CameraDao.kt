package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import com.serkantken.secuasist.models.Camera
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface CameraDao {
    /**
     * Tüm kameraları anlık olarak takip etmek için.
     * Arayüz bu Flow'u dinleyecek ve veri tabanındaki her değişiklikte kendini güncelleyecek.
     */
    @Query("SELECT * FROM cameras ORDER BY name ASC")
    fun getAllCameras(): Flow<List<Camera>>

    /**
     * Yeni bir kamera eklemek veya mevcut bir kamerayı güncellemek için.
     * OnConflictStrategy.REPLACE sayesinde, aynı id'ye sahip bir kamera gelirse,
     * eskisiyle değiştirilir, bu da hem ekleme hem güncelleme işini tek fonksiyonda birleştirir.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(camera: Camera)

    /**
     * ID'ye göre bir kamerayı silmek için.
     */
    @Query("DELETE FROM cameras WHERE id = :cameraId")
    suspend fun deleteById(cameraId: Int)

    /**
     * Bir kameranın arıza durumunu güncellemek için özel fonksiyon.
     * Bu, sadece iki alanı güncelleyerek daha verimli bir işlem sağlar.
     */
    @Query("UPDATE cameras SET isFaulty = :isFaulty, faultDate = :faultDate WHERE id = :cameraId")
    suspend fun updateFaultStatus(cameraId: Int, isFaulty: Boolean, faultDate: Date?)
}