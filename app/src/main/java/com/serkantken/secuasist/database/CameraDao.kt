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
    @Query("SELECT * FROM cameras ORDER BY name ASC")
    fun getAllCameras(): Flow<List<Camera>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(camera: Camera)

    @Query("DELETE FROM cameras WHERE id = :cameraId")
    suspend fun deleteById(cameraId: Int)

    @Query("UPDATE cameras SET isFaulty = :isFaulty, faultDate = :faultDate WHERE id = :cameraId")
    suspend fun updateFaultStatus(cameraId: Int, isFaulty: Boolean, faultDate: Date?)
}