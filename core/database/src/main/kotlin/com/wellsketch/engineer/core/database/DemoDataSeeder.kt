package com.wellsketch.engineer.core.database

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects

/**
 * Seeds the clearly labelled SYNTHETIC demo projects on first launch (empty database) so
 * the app is usable offline without any real well data.
 */
class DemoDataSeeder(private val dao: WellProjectDao) {
    suspend fun seedIfEmpty(nowEpochMillis: Long) {
        if (dao.count() > 0) return
        SyntheticDemoProjects.all(nowEpochMillis).forEach { dao.upsert(it.toEntity()) }
    }
}
