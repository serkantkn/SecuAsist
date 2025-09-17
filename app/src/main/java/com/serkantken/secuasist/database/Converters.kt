package com.serkantken.secuasist.database

import androidx.room.TypeConverter
import java.util.Date


class Converters {
    /**
     * Veritabanından Long olarak okunan timestamp'i Date nesnesine çevirir.
     * Değer null ise, null Date döndürür.
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /**
     * Koddaki Date nesnesini veritabanında saklamak için Long tipine çevirir.
     * Date null ise, null Long döndürür.
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}