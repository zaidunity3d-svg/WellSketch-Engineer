package com.wellsketch.engineer.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Local, offline-only database. No network or cloud sync exists in this milestone. */
@Database(entities = [WellProjectEntity::class], version = 1, exportSchema = true)
abstract class WellSketchDatabase : RoomDatabase() {
    abstract fun wellProjectDao(): WellProjectDao

    companion object {
        private const val FILE_NAME = "wellsketch.db"

        fun build(context: Context): WellSketchDatabase =
            Room.databaseBuilder(context.applicationContext, WellSketchDatabase::class.java, FILE_NAME).build()
    }
}
