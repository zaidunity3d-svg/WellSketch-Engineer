package com.wellsketch.engineer.core.database

import android.content.Context
import com.wellsketch.engineer.core.data.repository.WellProjectRepository

/**
 * Entry point to on-device storage. Callers get repository contracts only, so Room stays
 * an implementation detail of this module (other modules never see Room types).
 */
class LocalStorage(context: Context) {
    private val database = WellSketchDatabase.build(context)

    val wellProjects: WellProjectRepository = RoomWellProjectRepository(database.wellProjectDao())

    /**
     * Seeds the clearly labelled synthetic demo projects when the database is empty, and
     * upgrades demo projects stored by an earlier version with the current demo surface
     * equipment (real projects are never changed).
     */
    suspend fun seedDemoDataIfEmpty(nowEpochMillis: Long) {
        val seeder = DemoDataSeeder(database.wellProjectDao())
        seeder.seedIfEmpty(nowEpochMillis)
        seeder.upgradeSyntheticDemos()
    }
}
