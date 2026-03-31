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
        com.serkantken.secuasist.models.Villa::class,
        com.serkantken.secuasist.models.Contact::class,
        com.serkantken.secuasist.models.VillaContact::class,
        com.serkantken.secuasist.models.CargoCompany::class,
        com.serkantken.secuasist.models.Cargo::class,
        com.serkantken.secuasist.models.Camera::class,
        com.serkantken.secuasist.models.Intercom::class,
        com.serkantken.secuasist.models.CompanyDelivererCrossRef::class,
        com.serkantken.secuasist.models.CameraVisibleVillaCrossRef::class,
        com.serkantken.secuasist.models.SyncLog::class
    ],
    version = 7,
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
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE Villas ADD COLUMN isCallOnlyMobile INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS Users (
                        userId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        username TEXT NOT NULL,
                        password TEXT NOT NULL,
                        role TEXT NOT NULL,
                        displayName TEXT,
                        updatedAt INTEGER NOT NULL,
                        deviceId TEXT
                    )
                """.trimIndent())
                // Ensure unique usernames
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Users_username ON Users(username)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "secuasist_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
