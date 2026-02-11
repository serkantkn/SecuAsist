package com.serkantken.secuasist.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.serkantken.secuasist.models.Camera
import com.serkantken.secuasist.models.Cargo
import com.serkantken.secuasist.models.CargoCompany
import com.serkantken.secuasist.models.CompanyDelivererCrossRef
import com.serkantken.secuasist.models.Contact
import com.serkantken.secuasist.models.Villa
import com.serkantken.secuasist.models.VillaContact

@Database(
    entities = [
        Villa::class,
        Contact::class,
        VillaContact::class,
        CargoCompany::class,
        // CompanyContact removed
        Cargo::class,
        Camera::class,
        CompanyDelivererCrossRef::class,
        com.serkantken.secuasist.models.Intercom::class,
        com.serkantken.secuasist.models.CameraVisibleVillaCrossRef::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // DAO'ları tanımlayın
    abstract fun villaDao(): VillaDao
    abstract fun contactDao(): ContactDao
    abstract fun villaContactDao(): VillaContactDao
    abstract fun cargoCompanyDao(): CargoCompanyDao
    // abstract fun companyContactDao(): CompanyContactDao
    abstract fun cargoDao(): CargoDao
    abstract fun cameraDao(): CameraDao
    abstract fun intercomDao(): IntercomDao
    abstract fun companyDelivererDao(): CompanyDelivererDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "secuasist_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
