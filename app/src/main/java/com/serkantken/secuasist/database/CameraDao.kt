package com.serkantken.secuasist.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.serkantken.secuasist.models.Camera
import com.serkantken.secuasist.models.CameraVisibleVillaCrossRef
import com.serkantken.secuasist.models.CameraWithVillas
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(camera: Camera): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cameras: List<Camera>): List<Long>

    @Update
    suspend fun update(camera: Camera)

    @Delete
    suspend fun delete(camera: Camera)
    
    @Query("DELETE FROM Cameras")
    suspend fun deleteAll()
    
    @Query("DELETE FROM Cameras WHERE cameraId = :id")
    suspend fun deleteById(id: String)
    
    @Query("SELECT * FROM Cameras WHERE cameraId = :id")
    suspend fun getCameraById(id: String): Camera?

    @Query("SELECT * FROM Cameras")
    suspend fun getAllSync(): List<Camera>

    // --- Relations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: CameraVisibleVillaCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCrossRefs(crossRefs: List<CameraVisibleVillaCrossRef>)
    
    @Query("DELETE FROM CameraVisibleVillas WHERE cameraId = :cameraId")
    suspend fun deleteCrossRefsForCamera(cameraId: String)

    @Query("DELETE FROM CameraVisibleVillas WHERE cameraId = :cameraId AND villaId = :villaId")
    suspend fun deleteCrossRef(cameraId: String, villaId: Int)

    @Query("DELETE FROM CameraVisibleVillas")
    suspend fun deleteAllCrossRefs()

    @Query("SELECT * FROM CameraVisibleVillas")
    suspend fun getAllCrossRefsSync(): List<CameraVisibleVillaCrossRef>

    @Transaction
    @Query("SELECT * FROM Cameras")
    fun getAllCamerasWithVillas(): Flow<List<CameraWithVillas>>
    
    @Transaction
    @Query("SELECT * FROM Cameras WHERE cameraId IN (SELECT cameraId FROM CameraVisibleVillas WHERE villaId = :villaId)")
    fun getCamerasForVilla(villaId: Int): Flow<List<Camera>>
}