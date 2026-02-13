package com.serkantken.secuasist.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantken.secuasist.SecuAsistApplication
import com.serkantken.secuasist.models.Camera
import com.serkantken.secuasist.models.CameraVisibleVillaCrossRef
import com.serkantken.secuasist.models.CameraWithVillas
import com.serkantken.secuasist.models.Intercom
import com.serkantken.secuasist.models.Villa
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class FaultViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SecuAsistApplication
    private val cameraDao = app.db.cameraDao()
    private val intercomDao = app.db.intercomDao()
    private val villaDao = app.db.villaDao()

    val allVillas: Flow<List<Villa>> = villaDao.getAllVillas()

    // --- Cameras ---

    val allCameras: Flow<List<CameraWithVillas>> = cameraDao.getAllCamerasWithVillas()

    fun addCamera(name: String, ip: String, visibleVillaIds: List<Int>) {
        viewModelScope.launch {
            val camera = Camera(
                cameraName = name,
                cameraIp = ip
            )
            val insertedId = cameraDao.insert(camera) // Returns Long rowId? No, need UUID. Camera generates UUID by default now.
            // Wait, the DAO insert returns Long (RowId). 
            // Better to instantiate with fixed ID, insert, then use that ID.
            
            // Camera Primary Key is String (UUID). Room insert returning Long is just rowid, irrelevant for us.
            // We use the object's ID.
            
            app.syncManager.sendData("ADD_CAMERA", camera)
            
            visibleVillaIds.forEach { vid ->
                val crossRef = CameraVisibleVillaCrossRef(camera.cameraId, vid)
                cameraDao.insertCrossRef(crossRef)
                app.syncManager.sendData("ADD_CAMERA_VISIBLE_VILLA", mapOf("cameraId" to camera.cameraId, "villaId" to vid, "updatedAt" to System.currentTimeMillis()))
            }
        }
    }

    fun updateCameraStatus(camera: Camera, isWorking: Boolean) {
        viewModelScope.launch {
            val updated = camera.copy(
                isWorking = isWorking,
                lastChecked = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            cameraDao.update(updated)
            app.syncManager.sendData("UPDATE_CAMERA_STATUS", updated)
        }
    }
    
    fun deleteCamera(camera: Camera) {
        viewModelScope.launch {
            cameraDao.delete(camera)
            app.syncManager.sendData("DELETE_CAMERA", mapOf("cameraId" to camera.cameraId))
        }
    }

    // --- Intercoms ---

    fun getIntercomsForVilla(villaId: Int): Flow<List<Intercom>> = intercomDao.getIntercomsForVilla(villaId)

    fun addIntercom(villaId: Int, name: String) {
        viewModelScope.launch {
            val intercom = Intercom(
                villaId = villaId,
                intercomName = name
            )
            intercomDao.insert(intercom)
            app.syncManager.sendData("ADD_INTERCOM", intercom)
        }
    }

    fun updateIntercomStatus(intercom: Intercom, isWorking: Boolean) {
        viewModelScope.launch {
            val updated = intercom.copy(
                isWorking = isWorking,
                lastChecked = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            intercomDao.update(updated)
            app.syncManager.sendData("UPDATE_INTERCOM_STATUS", updated)
        }
    }

    fun deleteIntercom(intercom: Intercom) {
        viewModelScope.launch {
            intercomDao.delete(intercom)
            app.syncManager.sendData("DELETE_INTERCOM", mapOf("intercomId" to intercom.intercomId))
        }
    }
}
