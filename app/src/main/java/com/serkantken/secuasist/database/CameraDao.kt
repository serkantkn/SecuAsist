package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.serkantken.secuasist.models.Camera
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {
    @Insert
    suspend fun insert(camera: Camera): Long

    @Update
    suspend fun update(camera: Camera)

    @Delete
    suspend fun delete(camera: Camera)

    @Query("SELECT * FROM Cameras WHERE villaId = :villaId")
    fun getCamerasForVilla(villaId: Int): Flow<List<Camera>>

    @Query("SELECT * FROM Cameras WHERE cameraId = :cameraId")
    suspend fun getCameraById(cameraId: Int): Camera?
}