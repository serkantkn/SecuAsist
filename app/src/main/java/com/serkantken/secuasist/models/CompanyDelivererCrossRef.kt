package com.serkantken.secuasist.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "CompanyContacts", // Aligned with Server
    primaryKeys = ["companyId", "contactId"],
    foreignKeys = [
        ForeignKey(
            entity = CargoCompany::class,
            parentColumns = ["companyId"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Contact::class,
            parentColumns = ["contactId"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["contactId"])]
)
data class CompanyDelivererCrossRef(
    val companyId: Int,
    val contactId: String, // Updated to String (UUID)
    val role: String? = "Driver",
    val isPrimaryContact: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String? = null
)