package com.wellsketch.engineer.feature.wellcalc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wellsketch.engineer.core.data.repository.WellProjectRepository
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.volumes.VolumeBreakdown
import com.wellsketch.engineer.core.engineering.volumes.WellboreGeometry
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.units.Density
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.domain.wellcalc.AnnulusBoundary
import com.wellsketch.engineer.domain.wellcalc.CementExcessPolicy
import com.wellsketch.engineer.domain.wellcalc.FluidColumn
import com.wellsketch.engineer.domain.wellcalc.HydrostaticCalculator
import com.wellsketch.engineer.domain.wellcalc.HydrostaticResult
import com.wellsketch.engineer.domain.wellcalc.WellCalculationReport
import com.wellsketch.engineer.domain.wellcalc.WellCalculations
import com.wellsketch.engineer.domain.wellcalc.ObjectVolumeCalculator
import com.wellsketch.engineer.domain.wellcalc.ObjectVolumeMode
import com.wellsketch.engineer.domain.wellcalc.VolumePipe
import com.wellsketch.engineer.domain.wellcalc.WellVolumeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** User inputs of the Well Calculations screen (SI values; never persisted to the project). */
data class WellCalcInputs(
    val completionState: CompletionState? = null,
    val fluidDensity: Density = Density.kilogramsPerCubicMeter(1000.0),
    /** Canonical MD of the hydrostatic target; null = deepest accessible depth. */
    val hydrostaticTargetMd: Length? = null,
    val surfacePressure: Pressure = Pressure.ZERO,
    val openHoleExcessPercent: Double = 0.0,
    val casedHoleExcessPercent: Double = 0.0,
    val includeRathole: Boolean = false,
    /** Interval for the free volume tool (canonical MD); null = flow-path top / accessible bottom. */
    val intervalTopMd: Length? = null,
    val intervalBottomMd: Length? = null,
    /** Volume by named objects: pipe (null = first), mode, outer wall and interval (null = default overlap). */
    val objectPipeId: String? = null,
    val objectMode: ObjectVolumeMode = ObjectVolumeMode.ANNULUS,
    val objectBoundary: AnnulusBoundary = AnnulusBoundary.Automatic,
    val objectTopMd: Length? = null,
    val objectBottomMd: Length? = null,
)

/** Inputs and result of the "volume by named objects" card. */
data class ObjectVolumeUi(
    val pipes: List<VolumePipe>,
    val pipe: VolumePipe,
    val mode: ObjectVolumeMode,
    val boundaries: List<AnnulusBoundary>,
    val boundary: AnnulusBoundary,
    val topMd: Length,
    val bottomMd: Length,
    val result: VolumeBreakdown,
)

data class WellCalcUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val project: WellProject? = null,
    val inputs: WellCalcInputs = WellCalcInputs(),
    val availableStates: List<CompletionState> = emptyList(),
    val report: WellCalculationReport? = null,
    val interval: VolumeBreakdown? = null,
    val intervalTopMd: Length? = null,
    val intervalBottomMd: Length? = null,
    val hydrostatic: HydrostaticResult? = null,
    val hydrostaticTargetMd: Length? = null,
    val fluidTopMd: Length? = null,
    /** Equivalent density at the target, referenced to RKB. */
    val equivalentDensity: Density? = null,
    val objectVolume: ObjectVolumeUi? = null,
)

sealed interface WellCalcEvent {
    data class SelectCompletionState(val state: CompletionState) : WellCalcEvent
    data class SetFluidDensity(val density: Density) : WellCalcEvent
    data class SetHydrostaticTarget(val md: Length) : WellCalcEvent
    data class SetSurfacePressure(val pressure: Pressure) : WellCalcEvent
    data class SetOpenHoleExcess(val percent: Double) : WellCalcEvent
    data class SetIncludeRathole(val include: Boolean) : WellCalcEvent
    data class SetIntervalTop(val md: Length) : WellCalcEvent
    data class SetIntervalBottom(val md: Length) : WellCalcEvent
    data class SetCasedHoleExcess(val percent: Double) : WellCalcEvent
    data class SelectObjectPipe(val pipeId: String) : WellCalcEvent
    data class SelectObjectMode(val mode: ObjectVolumeMode) : WellCalcEvent
    data class SelectObjectBoundary(val boundary: AnnulusBoundary) : WellCalcEvent
    data class SetObjectTop(val md: Length) : WellCalcEvent
    data class SetObjectBottom(val md: Length) : WellCalcEvent
}

/**
 * Well Calculations state. Every result is recomputed synchronously from the project and
 * the inputs by the pure-Kotlin calculators in :domain:wellcalc; nothing is cached.
 */
class WellCalculationsViewModel(
    private val projectId: String,
    private val repository: WellProjectRepository,
) : ViewModel() {

    private val calculations = WellCalculations()
    private val volumes = WellVolumeCalculator()
    private val objectVolumes = ObjectVolumeCalculator(volumes)
    private val project = MutableStateFlow<WellProject?>(null)
    private val inputs = MutableStateFlow(WellCalcInputs())
    private val loadError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WellCalcUiState> =
        combine(project, inputs, loadError) { p, input, error ->
            when {
                error != null -> WellCalcUiState(loading = false, error = error)
                p == null -> WellCalcUiState(loading = true)
                else -> compute(p, input)
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WellCalcUiState())

    init {
        viewModelScope.launch {
            runCatching { repository.getProject(projectId) }
                .onSuccess { loaded -> if (loaded == null) loadError.value = "Project not found." else project.value = loaded }
                .onFailure { loadError.value = "Could not load project: ${it.message}" }
        }
    }

    fun onEvent(event: WellCalcEvent) {
        val i = inputs.value
        inputs.value = when (event) {
            is WellCalcEvent.SelectCompletionState -> i.copy(completionState = event.state)
            is WellCalcEvent.SetFluidDensity -> i.copy(fluidDensity = event.density)
            is WellCalcEvent.SetHydrostaticTarget -> i.copy(hydrostaticTargetMd = event.md)
            is WellCalcEvent.SetSurfacePressure -> i.copy(surfacePressure = event.pressure)
            is WellCalcEvent.SetOpenHoleExcess -> i.copy(openHoleExcessPercent = event.percent)
            is WellCalcEvent.SetIncludeRathole -> i.copy(includeRathole = event.include)
            is WellCalcEvent.SetIntervalTop -> i.copy(intervalTopMd = event.md)
            is WellCalcEvent.SetIntervalBottom -> i.copy(intervalBottomMd = event.md)
            is WellCalcEvent.SetCasedHoleExcess -> i.copy(casedHoleExcessPercent = event.percent)
            // A new pipe, mode or outer wall resets the interval to its default overlap.
            is WellCalcEvent.SelectObjectPipe -> i.copy(objectPipeId = event.pipeId, objectBoundary = AnnulusBoundary.Automatic, objectTopMd = null, objectBottomMd = null)
            is WellCalcEvent.SelectObjectMode -> i.copy(objectMode = event.mode, objectTopMd = null, objectBottomMd = null)
            is WellCalcEvent.SelectObjectBoundary -> i.copy(objectBoundary = event.boundary, objectTopMd = null, objectBottomMd = null)
            is WellCalcEvent.SetObjectTop -> i.copy(objectTopMd = event.md)
            is WellCalcEvent.SetObjectBottom -> i.copy(objectBottomMd = event.md)
        }
    }

    private fun compute(p: WellProject, input: WellCalcInputs): WellCalcUiState {
        val well = p.well
        val available = CompletionState.entries.filter { well.completion(it) != null }
        val state = input.completionState?.takeIf { it in available } ?: available.lastOrNull()
        val policy = CementExcessPolicy(
            openHoleExcessFraction = (input.openHoleExcessPercent / 100.0).coerceIn(0.0, CementExcessPolicy.MAX_EXCESS),
            casedHoleExcessFraction = (input.casedHoleExcessPercent / 100.0).coerceIn(0.0, CementExcessPolicy.MAX_EXCESS),
            includeRathole = input.includeRathole,
        )
        val report = calculations.report(well, state, policy)
        val geometry = report.geometry
        val top = volumes.flowPathTop(geometry)
        val bottom = volumes.accessibleBottom(geometry)

        val intervalTop = input.intervalTopMd ?: top
        val intervalBottom = input.intervalBottomMd ?: bottom
        val interval = if (intervalTop != null && intervalBottom != null) volumes.flowPathVolume(geometry, intervalTop, intervalBottom) else null

        val target = input.hydrostaticTargetMd ?: bottom
        val ctx: WellGeometryContext = geometry.ctx
        val hydro = if (top != null && bottom != null && target != null) {
            val columnBottom = maxOf(bottom, target)
            HydrostaticCalculator.pressureAt(ctx, listOf(FluidColumn("Fluid", input.fluidDensity, top, columnBottom)), target, input.surfacePressure)
        } else {
            null
        }
        val objectVolume = objectVolumeUi(geometry, input)
        return WellCalcUiState(
            loading = false,
            project = p,
            inputs = input,
            availableStates = available,
            report = report,
            interval = interval,
            intervalTopMd = intervalTop,
            intervalBottomMd = intervalBottom,
            hydrostatic = hydro,
            hydrostaticTargetMd = target,
            fluidTopMd = top,
            equivalentDensity = hydro?.let { HydrostaticCalculator.equivalentDensityAt(ctx, it.totalPressure, it.targetMd) },
            objectVolume = objectVolume,
        )
    }

    private fun objectVolumeUi(geometry: WellboreGeometry, input: WellCalcInputs): ObjectVolumeUi? {
        val pipes = objectVolumes.pipes(geometry)
        val pipe = pipes.firstOrNull { it.id == input.objectPipeId } ?: pipes.firstOrNull() ?: return null
        val boundaries = objectVolumes.boundaries(geometry, pipe)
        val boundary = input.objectBoundary.takeIf { it in boundaries } ?: AnnulusBoundary.Automatic
        val default = objectVolumes.defaultInterval(geometry, pipe, input.objectMode, boundary) ?: (pipe.top to pipe.bottom)
        val top = input.objectTopMd ?: default.first
        val bottom = input.objectBottomMd ?: default.second
        val result = objectVolumes.calculate(geometry, pipe.id, input.objectMode, boundary, top, bottom)
        return ObjectVolumeUi(pipes, pipe, input.objectMode, boundaries, boundary, top, bottom, result)
    }
}
