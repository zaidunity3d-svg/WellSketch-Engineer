package com.wellsketch.engineer.feature.ctcalc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wellsketch.engineer.core.data.repository.ProjectAutosaver
import com.wellsketch.engineer.core.data.repository.WellProjectRepository
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.ct.CtOperation
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.project.WellProject
import com.wellsketch.engineer.core.model.units.FlowRate
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.schematic.CtSurfaceViewMode
import com.wellsketch.engineer.core.schematic.SchematicElement
import com.wellsketch.engineer.core.schematic.SurfaceBlock
import com.wellsketch.engineer.domain.ct.CtAnalysis
import com.wellsketch.engineer.domain.ct.CtOperationAnalyzer
import com.wellsketch.engineer.domain.ct.CtOperationEdits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Kinds of depth reference the user can pick per CT operation. */
enum class DepthReferenceChoice(val label: String) { RKB("RKB"), GL("GL"), OPERATION("Operation datum") }

data class CtUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val project: WellProject? = null,
    val operation: CtOperation? = null,
    val analysis: CtAnalysis? = null,
    val viewMode: CtSurfaceViewMode = CtSurfaceViewMode.COMPACT,
    val selectedElement: SchematicElement? = null,
    val selectedBlock: SurfaceBlock? = null,
)

sealed interface CtEvent {
    data class SetDepth(val depth: Length) : CtEvent
    data class SetPumpRate(val rate: FlowRate) : CtEvent
    data class SetViewMode(val mode: CtSurfaceViewMode) : CtEvent
    data class SetReference(val choice: DepthReferenceChoice) : CtEvent
    data class SetOperationDatumHeight(val heightAboveGl: Length) : CtEvent
    data class SelectElement(val element: SchematicElement?) : CtEvent
    data class SelectBlock(val block: SurfaceBlock) : CtEvent
    data object ClearSelection : CtEvent
}

/**
 * CT module state. Every depth change produces a new analysis synchronously; the CT and
 * BHA jump to the new position with no animation, as required.
 */
class CtCalculationsViewModel(
    private val projectId: String,
    private val repository: WellProjectRepository,
    private val autosaver: ProjectAutosaver,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val analyzer = CtOperationAnalyzer()
    private val project = MutableStateFlow<WellProject?>(null)
    private val viewMode = MutableStateFlow(CtSurfaceViewMode.COMPACT)
    private val selectedElement = MutableStateFlow<SchematicElement?>(null)
    private val selectedBlock = MutableStateFlow<SurfaceBlock?>(null)
    private val loadError = MutableStateFlow<String?>(null)

    /** Last operation-specific datum, so switching RKB/GL and back keeps its explicit offset. */
    private var lastOperationDatum: DepthReference.Operation? = null

    val uiState: StateFlow<CtUiState> =
        combine(project, viewMode, selectedElement, selectedBlock, loadError) { p, mode, element, block, error ->
            when {
                error != null -> CtUiState(loading = false, error = error)
                p == null -> CtUiState(loading = true)
                else -> {
                    val op = p.ctOperations.firstOrNull()
                    CtUiState(
                        loading = false,
                        error = if (op == null) "No coiled-tubing operation is defined for this project." else null,
                        project = p,
                        operation = op,
                        analysis = op?.let { analyzer.analyze(p.well, it, mode) },
                        viewMode = mode,
                        selectedElement = element,
                        selectedBlock = block,
                    )
                }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CtUiState())

    init {
        viewModelScope.launch {
            runCatching { repository.getProject(projectId) }
                .onSuccess { loaded ->
                    if (loaded == null) {
                        loadError.value = "Project not found."
                    } else {
                        lastOperationDatum = loaded.ctOperations.firstOrNull()?.depthReference as? DepthReference.Operation
                        project.value = loaded
                    }
                }
                .onFailure { loadError.value = "Could not load project: ${it.message}" }
        }
    }

    fun onEvent(event: CtEvent) {
        when (event) {
            is CtEvent.SetDepth -> updateOperation { _, op -> CtOperationEdits.setDepth(op, event.depth) }
            is CtEvent.SetPumpRate -> updateOperation { _, op -> CtOperationEdits.setPumpRate(op, event.rate) }
            is CtEvent.SetViewMode -> viewMode.value = event.mode
            is CtEvent.SetReference -> updateOperation { ctx, op ->
                val target = when (event.choice) {
                    DepthReferenceChoice.RKB -> DepthReference.Rkb
                    DepthReferenceChoice.GL -> DepthReference.GroundLevel
                    DepthReferenceChoice.OPERATION -> lastOperationDatum
                        ?: DepthReference.Operation("${op.id}-datum", "CT zero", Length.ZERO).also { lastOperationDatum = it }
                }
                CtOperationEdits.changeDepthReference(ctx, op, target)
            }
            is CtEvent.SetOperationDatumHeight -> updateOperation { ctx, op ->
                val current = op.depthReference as? DepthReference.Operation ?: return@updateOperation op
                val redefined = current.copy(heightAboveGroundLevel = event.heightAboveGl)
                lastOperationDatum = redefined
                // Redefining the datum keeps the tool where it is; the displayed depth changes.
                CtOperationEdits.changeDepthReference(ctx, op, redefined)
            }
            is CtEvent.SelectElement -> {
                selectedElement.value = event.element
                selectedBlock.value = null
            }
            is CtEvent.SelectBlock -> {
                selectedBlock.value = event.block
                selectedElement.value = null
            }
            CtEvent.ClearSelection -> {
                selectedElement.value = null
                selectedBlock.value = null
            }
        }
    }

    private fun updateOperation(transform: (WellGeometryContext, CtOperation) -> CtOperation) {
        val current = project.value ?: return
        val op = current.ctOperations.firstOrNull() ?: return
        val updatedOp = transform(WellGeometryContext(current.well), op)
        if (updatedOp == op) return
        val updated = current.copy(
            ctOperations = current.ctOperations.map { if (it.id == op.id) updatedOp else it },
            lastModifiedEpochMillis = clock(),
        )
        project.value = updated
        autosaver.submit(updated)
    }
}
