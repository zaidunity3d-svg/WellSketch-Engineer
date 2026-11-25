package com.wellsketch.engineer.core.ui.components

import com.wellsketch.engineer.core.engineering.surface.SurfaceStackup
import com.wellsketch.engineer.core.model.surface.CtPressureControlStack
import com.wellsketch.engineer.core.model.surface.SideOutlet
import com.wellsketch.engineer.core.model.surface.StackComponent
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.schematic.SurfaceBlock
import com.wellsketch.engineer.core.ui.format.UnitFormatter

/**
 * Detail rows for a tapped surface item, built from the stored component data (wellhead
 * section, tree or wing component, drilling BOP component, CT stack component, cellar or
 * rig). Elevations are the drawn positions, which come from entered heights where given.
 */
fun surfaceObjectDetails(
    block: SurfaceBlock,
    well: Well,
    ctStack: CtPressureControlStack?,
    stackup: SurfaceStackup?,
    f: UnitFormatter,
): ObjectDetails {
    fun len(v: Length?) = v?.let(f::depth) ?: "Not provided"
    fun dia(v: Length?) = v?.let(f::diameter) ?: "Not provided"
    fun psi(v: Pressure?) = v?.let(f::pressure) ?: "Not provided"
    fun elevation(m: Double) = Length.meters(m).let { if (it < Length.ZERO) "${f.depth(-it)} below GL" else "${f.depth(it)} above GL" }
    val position = listOf(
        "Base" to elevation(block.bottomAboveGl),
        "Top" to elevation(block.topAboveGl),
    )
    fun outlets(list: List<SideOutlet>) = list.map { o ->
        "${o.side.label} outlet" to (o.label ?: o.fittings.joinToString { it.label })
    }

    well.wellhead?.sections?.firstOrNull { it.id == block.sourceId }?.let { s ->
        return ObjectDetails(
            s.name,
            listOf(
                "Type" to s.type.label,
                "Bore" to dia(s.bore),
                "Rated working pressure" to psi(s.ratedWorkingPressure),
                "Top connection" to s.topConnection.designation,
                "Bottom connection" to s.bottomConnection.designation,
                "Height" to len(s.height),
                "Suspends" to (s.suspendedStringId?.let { id -> well.casingStrings.firstOrNull { it.id == id }?.name ?: id } ?: "Not recorded"),
            ) + position + outlets(s.sideOutlets),
            s.provenance,
        )
    }
    val tree = well.christmasTree
    val treeComponent = tree?.components?.firstOrNull { it.id == block.sourceId }
        ?: tree?.wingOutlets?.flatMap { it.components }?.firstOrNull { it.id == block.sourceId }
    val stackComponent = treeComponent ?: ctStack?.components?.firstOrNull { it.id == block.sourceId }
    if (stackComponent != null) return stackDetails(stackComponent, block, position, f, ::len, ::dia)

    well.drillingRigUp?.let { rigUp ->
        rigUp.bopStack.components.firstOrNull { it.id == block.sourceId }?.let { c ->
            return ObjectDetails(
                c.name,
                listOfNotNull(
                    "Type" to c.type.label,
                    "Bore" to dia(c.bore),
                    "Rated working pressure" to psi(c.ratedWorkingPressure),
                    c.rams.takeIf { it.isNotEmpty() }?.let { rams -> "Rams (top to bottom)" to rams.joinToString { it.label } },
                    "Top connection" to c.topConnection.designation,
                    "Bottom connection" to c.bottomConnection.designation,
                    "Height" to len(c.height),
                ) + position + outlets(c.sideOutlets),
                c.provenance,
            )
        }
        if (block.sourceId == rigUp.rig.id) {
            val rig = rigUp.rig
            return ObjectDetails(
                rig.name,
                listOf(
                    "RKB above GL" to f.depth(well.datums.rkbAboveGroundLevel),
                    "Drill floor below RKB" to len(rig.floorBelowRkb),
                    "Drill floor above GL" to (stackup?.drillFloor?.let(f::depth) ?: "Not calculated"),
                    "Clear height under rotary beams" to len(rig.clearHeightUnderRotaryBeams),
                    "BOP stack top above GL" to (stackup?.drillingStackTop?.let(f::depth) ?: "Not calculated"),
                    "Clearance under rotary beams" to (stackup?.clearanceUnderRotaryBeams?.let(f::depth) ?: "Not calculated"),
                    "Substructure width" to len(rig.substructureWidth),
                ),
                rig.provenance,
            )
        }
    }
    well.cellar?.takeIf { block.sourceId == "cellar" }?.let { c ->
        return ObjectDetails(
            "Cellar",
            listOf(
                "Depth below GL" to f.depth(c.depthBelowGroundLevel),
                "Inside width" to len(c.insideWidth),
                "Wellhead base" to (stackup?.wellheadBase?.let { elevation(it.meters) } ?: "Not calculated"),
                "Wellhead top" to (stackup?.wellheadTop?.let { elevation(it.meters) } ?: "Not calculated"),
            ),
            c.provenance,
        )
    }
    return ObjectDetails(
        block.label,
        listOf("Size" to if (block.sizeIsSchematic) "Drawing placeholder – no data-sheet dimensions" else f.depth(Length.meters(block.height))) + position,
        null,
    )
}

private fun stackDetails(
    c: StackComponent,
    block: SurfaceBlock,
    position: List<Pair<String, String>>,
    f: UnitFormatter,
    len: (Length?) -> String,
    dia: (Length?) -> String,
): ObjectDetails {
    val horizontal = block.drawing.side != 0
    return ObjectDetails(
        c.name,
        listOfNotNull(
            "Type" to c.type.label,
            "Bore" to dia(c.bore),
            "Rated working pressure" to f.pressure(c.ratedWorkingPressure),
            c.operator?.let { "Operator" to it.label },
            "Top connection" to c.topConnection.designation,
            "Bottom connection" to c.bottomConnection.designation,
            (if (horizontal) "Face-to-face length" else "Height") to len(c.height),
        ) + if (horizontal) emptyList() else position,
        c.provenance,
    )
}
