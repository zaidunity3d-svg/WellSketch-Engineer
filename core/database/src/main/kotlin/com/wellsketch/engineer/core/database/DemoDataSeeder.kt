package com.wellsketch.engineer.core.database

import com.wellsketch.engineer.core.data.codec.WellProjectCodec
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

    /**
     * Adds the current demo surface equipment (wellhead, cellar, drilling rig-up, tree) to
     * synthetic demo projects stored by an earlier version. Real projects are never changed;
     * a stored document that cannot be decoded is left as it is.
     */
    suspend fun upgradeSyntheticDemos() {
        for (id in SyntheticDemoProjects.all().map { it.id }) {
            val entity = dao.get(id) ?: continue
            val stored = runCatching { WellProjectCodec.decode(entity.payloadJson) }.getOrNull() ?: continue
            SyntheticDemoProjects.withCurrentSurfaceEquipment(stored)?.let { dao.upsert(it.toEntity()) }
        }
    }
}
