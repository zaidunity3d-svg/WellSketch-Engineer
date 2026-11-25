package com.wellsketch.engineer

import android.content.Context
import android.util.Log
import com.wellsketch.engineer.core.data.demo.SyntheticEquipmentCatalog
import com.wellsketch.engineer.core.data.repository.EquipmentCatalogRepository
import com.wellsketch.engineer.core.data.repository.InMemoryEquipmentCatalogRepository
import com.wellsketch.engineer.core.data.repository.ProjectAutosaver
import com.wellsketch.engineer.core.data.repository.WellProjectRepository
import com.wellsketch.engineer.core.database.LocalStorage
import com.wellsketch.engineer.core.model.units.UnitSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manual dependency container (no DI framework in this milestone; see README for the
 * decision). Created once by [WellSketchApplication].
 */
class AppContainer(context: Context) {
    /** Application-wide scope for work that must outlive screens (autosave, seeding). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val storage = LocalStorage(context)

    val wellProjects: WellProjectRepository = storage.wellProjects

    /** Synthetic placeholder catalog until validated datasets are imported. */
    val equipmentCatalog: EquipmentCatalogRepository = InMemoryEquipmentCatalogRepository(SyntheticEquipmentCatalog.items)

    val autosaver = ProjectAutosaver(appScope, wellProjects) { error ->
        Log.e("WellSketch", "Autosave failed", error)
    }

    private val unitSystemState = MutableStateFlow(UnitSystem.OILFIELD)
    val unitSystem: StateFlow<UnitSystem> = unitSystemState.asStateFlow()

    fun setUnitSystem(system: UnitSystem) {
        unitSystemState.value = system
    }

    init {
        appScope.launch {
            storage.seedDemoDataIfEmpty(System.currentTimeMillis())
        }
    }
}
