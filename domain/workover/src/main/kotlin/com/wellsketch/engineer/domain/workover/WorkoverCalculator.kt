package com.wellsketch.engineer.domain.workover

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.engineering.volumes.DiameterBasis
import com.wellsketch.engineer.core.engineering.volumes.TubularGeometry
import com.wellsketch.engineer.core.engineering.volumes.VolumeBreakdown
import com.wellsketch.engineer.core.engineering.volumes.VolumeSegment
import com.wellsketch.engineer.core.engineering.volumes.WellboreGeometry
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.units.Angle
import com.wellsketch.engineer.core.model.units.Density
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure
import com.wellsketch.engineer.core.model.units.UnitDefinitions
import com.wellsketch.engineer.core.model.units.Volume
import com.wellsketch.engineer.core.model.units.VolumePerLength
import com.wellsketch.engineer.core.model.well.Well
import com.wellsketch.engineer.core.model.workover.WorkoverOperation
import com.wellsketch.engineer.core.model.workover.WorkoverOperationType
import com.wellsketch.engineer.core.model.workover.WorkoverToolType
import kotlin.math.ceil

/** A work-string section or tool at its depth (canonical well MD). */
data class WorkoverItem(
    val id: String,
    val name: String,
    val label: String,
    val top: Length,
    val bottom: Length,
    val outerDiameter: Length,
    /** Through bore; null = closed. */
    val innerDiameter: Length?,
    /** Largest OD for clearance (tool joint / coupling if recorded). */
    val clearanceOuterDiameter: Length,
    val isTool: Boolean,
    val toolType: WorkoverToolType? = null,
) {
    val length: Length get() = bottom - top
}

/** Smallest passage met by one item on its way down to its position. */
data class WorkoverClearance(
    val itemId: String,
    val itemName: String,
    val outerDiameter: Length,
    val restrictionName: String,
    val restriction: Length,
    /** "drift", "ID", "bit size" or "BOP bore". */
    val basis: String,
    val clearance: Length,
)

data class CementPlugResult(
    val plugTop: Length,
    val plugBottom: Length,
    val slurryVolume: Volume,
    /** Top of the slurry while the pipe is still in it (balanced). */
    val topWithPipeIn: Length,
    val spacerAhead: Volume,
    val spacerBehind: Volume,
    val displacement: Volume,
)

data class AcidResult(
    val treatmentVolume: Volume,
    /** Fluid pumped behind the acid to bring its front to the top of the interval. */
    val displacementToTop: Volume,
    val overflush: Volume,
    val maxSurfacePressure: Pressure?,
)

data class PerforationResult(
    val intervalLength: Length,
    val shots: Int?,
    val gunRuns: Int?,
    /** Gun interval when the string is at its bottom depth (canonical MD), if guns are in the tools. */
    val gunTop: Length?,
    val gunBottom: Length?,
)

data class PackerResult(
    val packerTop: Length,
    val packerBottom: Length,
    val hostName: String?,
    val hostInnerDiameter: Length?,
    val packerFluidVolume: Volume,
    val volumeBelowPacker: Volume?,
)

data class EspResult(
    val intakeDepth: Length?,
    val intakeTvdBelowDatum: Length?,
    val intakeInclination: Angle?,
    val motorBottom: Length?,
    val maxOuterDiameter: Length,
)

data class WorkoverAnalysis(
    val operation: WorkoverOperation,
    val rigFloorMd: Length,
    val wellTopMd: Length,
    val bottomMd: Length,
    val items: List<WorkoverItem>,
    val stringVolume: VolumeBreakdown,
    val annulus: VolumeBreakdown,
    /** Bore below the string down to the target bottom, when the target is deeper. */
    val belowString: VolumeBreakdown?,
    /** Bore volume from the well top to the target top with no string in the hole (bullheading). */
    val boreToTarget: VolumeBreakdown?,
    /** Steel volume of the string below the well top (fill when pulling dry, open end). */
    val openEndDisplacement: Volume,
    /** Steel + inside volume below the well top (fill when pulling wet / closed end). */
    val closedEndDisplacement: Volume,
    val surfaceToBottomMinutes: Double?,
    val bottomsUpMinutes: Double?,
    val hydrostaticAtTarget: Pressure?,
    /** Hydrostatic of the well fluid minus reservoir pressure (positive = overbalanced). */
    val balance: Pressure?,
    val killDensity: Density?,
    val targetTvdBelowDatum: Length?,
    val clearances: List<WorkoverClearance>,
    val cementPlug: CementPlugResult?,
    val acid: AcidResult?,
    val perforation: PerforationResult?,
    val packer: PackerResult?,
    val esp: EspResult?,
    val issues: List<ValidationIssue>,
) {
    val tightest: WorkoverClearance? get() = clearances.minByOrNull { it.clearance }
    val circulationMinutes: Double? get() = surfaceToBottomMinutes?.let { a -> bottomsUpMinutes?.let { a + it } }
}

/**
 * Workover calculations (version [VERSION]) for a work string of drill pipe or tubing run from
 * a workover rig into the production casing / liner (production tubing pulled; completion
 * equipment left in the well is not modelled).
 *
 * Formulas (nominal body dimensions, tool joints only in clearance checks):
 * - inside capacity π/4·ID²; steel displacement π/4·(OD² − ID²); closed end π/4·OD²;
 * - annulus π/4·(D² − OD²) with D the innermost casing/liner ID, else the bit size;
 * - times = volume / pump rate;
 * - hydrostatic P = ρ·g·TVD with TVD below the operation datum (the workover RKB);
 *   kill density = (P_res + overbalance) / (g·TVD);
 * - balanced cement plug: V_s = L·C_bore·(1 + excess); with the pipe in, the slurry height H
 *   solves ∫(C_annulus + C_pipe) = V_s; spacer behind = spacer ahead·C_pipe/C_annulus;
 *   displacement = pipe volume to (bottom − H) − spacer behind − under-displacement;
 * - acid volume = volume per length × interval; MASP = fracture gradient·TVD − ρ·g·TVD
 *   (friction not included).
 * Every result is flagged for engineering review (status: implemented, pending validation).
 */
class WorkoverCalculator {

    fun analyze(well: Well, op: WorkoverOperation): WorkoverAnalysis {
        val ctx = WellGeometryContext(well)
        val geometry = WellboreGeometry(ctx, completion = null)
        val issues = mutableListOf<ValidationIssue>()
        fun md(v: Length) = ctx.canonical(MeasuredDepth(v, op.depthReference))
        val rigFloor = md(Length.ZERO)
        val wellTop = geometry.strings.minOfOrNull { it.top } ?: geometry.holes.minOfOrNull { it.top } ?: rigFloor
        val bottom = md(op.bottomDepth)

        // --- Layout: tools from the bottom up, then sections; the top section reaches the rig floor.
        val items = mutableListOf<WorkoverItem>()
        var cursor = bottom
        for (t in op.tools.asReversed()) {
            val top = cursor - t.length
            items += WorkoverItem(t.id, t.name, t.type.label, top, cursor, t.maxOuterDiameter, t.innerDiameter, t.maxOuterDiameter, true, t.type)
            cursor = top
        }
        val sections = op.workString.sections
        if (sections.isEmpty()) {
            issues += ValidationIssue(Severity.ERROR, "WO_NO_STRING", "Add at least one work-string section (drill pipe or tubing).")
        }
        for ((i, s) in sections.withIndex().reversed()) {
            val top = if (i == 0) rigFloor else cursor - s.length
            if (i == 0 && cursor <= rigFloor) {
                issues += ValidationIssue(
                    Severity.ERROR, "WO_STRING_TOO_LONG",
                    "The lower sections and tools are longer than the bottom depth: nothing is left for '${s.name}' up to the rig floor.",
                )
                continue
            }
            items += WorkoverItem(
                s.id, s.name, s.kind.label, top, cursor, s.spec.outerDiameter, s.spec.innerDiameter,
                maxOf(s.spec.outerDiameter, s.connectionOuterDiameter ?: s.spec.outerDiameter), false,
            )
            cursor = top
        }
        items.sortBy { it.top }
        val deepest = geometry.deepestMd
        if (deepest != null && bottom > deepest + Length.meters(TOL)) {
            issues += ValidationIssue(Severity.ERROR, "WO_BELOW_TD", "Bottom depth ${m(bottom)} m MD is below the deepest recorded hole/shoe (${m(deepest)} m MD).")
        }
        if (rigFloor > wellTop) {
            issues += ValidationIssue(Severity.WARNING, "WO_DATUM", "The operation datum is below the top of the well bore; check the workover RKB height.")
        }

        // --- Inside the string (rig floor to the first closed tool or the bottom).
        val stringSegments = mutableListOf<VolumeSegment>()
        var closedAt: WorkoverItem? = null
        for (it in items) {
            val bore = it.innerDiameter
            if (bore == null || bore.meters <= 0.0) { closedAt = it; break }
            stringSegments += VolumeSegment(it.top, it.bottom, "Inside ${it.name}", bore, null, DiameterBasis.STATED_ID, TubularGeometry.insideCapacity(bore), sourceId = it.id)
        }
        if (closedAt != null && op.type in CIRCULATING_TYPES) {
            issues += ValidationIssue(Severity.WARNING, "WO_CLOSED_STRING", "'${closedAt.name}' has no through bore: fluid cannot be pumped out of the string below ${m(closedAt.top)} m MD.")
        }
        val stringVolume = VolumeBreakdown("Inside work string", stringSegments)

        // --- Annulus from the top of the well bore to the bottom.
        val annulus = annulus(geometry, items, wellTop, bottom, issues)
        if (rigFloor < wellTop) {
            issues += ValidationIssue(
                Severity.INFO, "WO_SURFACE_EXCLUDED",
                "Annulus starts at the top of the casing (${m(wellTop)} m MD); the BOP and wellhead bore above it are not included.",
            )
        }

        // --- Pulling the string: steel and closed-end volumes below the well top.
        var openEnd = 0.0
        var closedEnd = 0.0
        for (it in items) {
            val a = maxOf(it.top, wellTop)
            if (it.bottom <= a) continue
            val len = (it.bottom - a).meters
            val id = it.innerDiameter ?: Length.ZERO
            openEnd += TubularGeometry.annularCapacity(it.outerDiameter, id).cubicMetersPerMeter * len
            closedEnd += TubularGeometry.insideCapacity(it.outerDiameter).cubicMetersPerMeter * len
        }

        // --- Target interval.
        val tTop = op.targetTop?.let(::md)
        val tBottom = op.targetBottom?.let(::md)
        if (tTop != null && tBottom != null && tBottom <= tTop) {
            issues += ValidationIssue(Severity.ERROR, "WO_TARGET", "Target bottom must be below the target top.")
        }
        val belowString = tBottom?.takeIf { it > bottom + Length.meters(TOL) }?.let { bore(geometry, bottom, it, "Below the string", issues) }
        val boreToTarget = tTop?.takeIf { it > wellTop }?.let { bore(geometry, wellTop, it, "Well bore to target top (no string)", issues) }

        // --- Times.
        val rate = op.pumpRate?.cubicMetersPerSecond?.takeIf { it > 0.0 }
        val toBottom = rate?.let { stringVolume.total.cubicMeters / it / 60.0 }
        val bottomsUp = rate?.let { annulus.total.cubicMeters / it / 60.0 }

        // --- Hydrostatics at the target (mid-interval), TVD below the operation datum.
        val targetMd = when {
            tTop != null && tBottom != null -> Length((tTop.meters + tBottom.meters) / 2.0)
            tTop != null -> tTop
            else -> bottom
        }
        val tvd = tvdBelowDatum(ctx, targetMd, op.depthReference)
        val hydro = op.wellFluidDensity?.let { hydrostatic(it, tvd) }
        val balance = hydro?.let { h -> op.reservoirPressure?.let { Pressure(h.pascals - it.pascals) } }
        val kill = op.reservoirPressure?.takeIf { tvd.meters > 0.0 }?.let { pr ->
            Density((pr.pascals + (op.killOverbalance?.pascals ?: 0.0)) / (G * tvd.meters))
        }

        // --- Clearance of each item on its way down.
        val clearances = items.mapNotNull { clearance(well, geometry, it, wellTop, op, issues) }

        // --- Operation-specific results.
        val plug = if (op.type == WorkoverOperationType.CEMENT_PLUG) cementPlug(geometry, items, stringSegments, bottom, op, issues) else null
        val acid = if (op.type == WorkoverOperationType.ACIDIZING) acid(geometry, ctx, stringVolume, bottom, tTop, tBottom, op, issues) else null
        val perf = if (op.type == WorkoverOperationType.PERFORATION) perforation(items, tTop, tBottom, op, issues) else null
        val packer = if (op.type == WorkoverOperationType.SET_PACKER) packer(geometry, items, annulus, tTop, issues) else null
        val esp = if (op.type == WorkoverOperationType.ESP_CHANGE) esp(ctx, items, tTop, op, issues) else null
        if (op.type == WorkoverOperationType.WELL_KILL && (op.reservoirPressure == null || tTop == null)) {
            issues += ValidationIssue(Severity.WARNING, "WO_KILL_INPUTS", "Enter the reservoir pressure and the target (perforation) top for the kill density and bullhead volume.")
        }
        if (balance != null && op.type == WorkoverOperationType.PERFORATION && balance.pascals < 0.0) {
            issues += ValidationIssue(Severity.WARNING, "WO_UNDERBALANCE", "Well fluid is underbalanced at the perforations by ${psi(-balance.pascals)} psi: plan well control.")
        }

        return WorkoverAnalysis(
            op, rigFloor, wellTop, bottom, items, stringVolume, annulus, belowString, boreToTarget,
            Volume(openEnd), Volume(closedEnd), toBottom, bottomsUp, hydro, balance, kill, tvd,
            clearances, plug, acid, perf, packer, esp, issues.distinct(),
        )
    }

    // ------------------------------------------------------------------------------------------

    private fun itemAt(items: List<WorkoverItem>, md: Length): WorkoverItem? =
        items.firstOrNull { md > it.top && md < it.bottom }

    private fun annulus(
        geometry: WellboreGeometry,
        items: List<WorkoverItem>,
        top: Length,
        bottom: Length,
        issues: MutableList<ValidationIssue>,
    ): VolumeBreakdown {
        if (bottom <= top) return VolumeBreakdown("Annulus", emptyList())
        val segments = mutableListOf<VolumeSegment>()
        for ((a, b) in geometry.breakpoints(top, bottom, items.flatMap { listOf(it.top, it.bottom) }).zipWithNext()) {
            val mid = Length((a.meters + b.meters) / 2.0)
            val item = itemAt(items, mid) ?: continue
            val outer = geometry.annulusBoundaryAt(mid, item.outerDiameter)
            if (outer == null) {
                issues += ValidationIssue(Severity.ERROR, "VOLUME_GEOMETRY_GAP", "No casing or hole recorded between ${m(a)} and ${m(b)} m MD.")
                continue
            }
            val d = outer.diameter
            val basis = outer.basis
            val src = outer.sourceId
            val name = if (basis == DiameterBasis.BIT_SIZE) "${outer.name} (open hole)" else outer.name
            if (d <= item.outerDiameter) {
                issues += ValidationIssue(Severity.ERROR, "ANNULUS_NO_CLEARANCE", "'${item.name}' OD does not fit in $name between ${m(a)} and ${m(b)} m MD.")
                continue
            }
            val seg = VolumeSegment(a, b, "$name × ${item.name}", d, item.outerDiameter, basis, TubularGeometry.annularCapacity(d, item.outerDiameter), sourceId = "$src/${item.id}")
            val last = segments.lastOrNull()
            if (last != null && last.sourceId == seg.sourceId && last.bottomMd == a) segments[segments.lastIndex] = last.copy(bottomMd = b) else segments += seg
        }
        if (segments.any { it.outerBasis == DiameterBasis.BIT_SIZE }) {
            issues += ValidationIssue(Severity.INFO, "VOLUME_BIT_SIZE", "Open-hole intervals use the nominal bit size; actual hole volume needs a caliper or an agreed excess.")
        }
        return VolumeBreakdown("Annulus", segments)
    }

    /** Fluid bore (innermost casing/liner, else open hole) with no pipe in it. */
    private fun bore(geometry: WellboreGeometry, top: Length, bottom: Length, title: String, issues: MutableList<ValidationIssue>): VolumeBreakdown {
        val segments = mutableListOf<VolumeSegment>()
        for ((a, b) in geometry.breakpoints(top, bottom).zipWithNext()) {
            val mid = Length((a.meters + b.meters) / 2.0)
            val s = geometry.innermostStringAt(mid)
            val seg = if (s != null) {
                VolumeSegment(a, b, "Inside ${s.name}", s.spec.innerDiameter, null, DiameterBasis.STATED_ID, TubularGeometry.insideCapacity(s.spec.innerDiameter), sourceId = s.id)
            } else {
                val h = geometry.holeAt(mid)
                if (h == null) {
                    issues += ValidationIssue(Severity.ERROR, "VOLUME_GEOMETRY_GAP", "No casing or hole recorded between ${m(a)} and ${m(b)} m MD.")
                    continue
                }
                VolumeSegment(a, b, "${h.name} (open hole)", h.bitDiameter, null, DiameterBasis.BIT_SIZE, TubularGeometry.insideCapacity(h.bitDiameter), sourceId = h.id)
            }
            val last = segments.lastOrNull()
            if (last != null && last.sourceId == seg.sourceId && last.bottomMd == a) segments[segments.lastIndex] = last.copy(bottomMd = b) else segments += seg
        }
        return VolumeBreakdown(title, segments)
    }

    private fun boreCapacityAt(geometry: WellboreGeometry, md: Length): Pair<Length, Boolean>? {
        geometry.innermostStringAt(md)?.let { return it.spec.innerDiameter to false }
        return geometry.holeAt(md)?.let { it.bitDiameter to true }
    }

    private fun clearance(
        well: Well,
        geometry: WellboreGeometry,
        item: WorkoverItem,
        wellTop: Length,
        op: WorkoverOperation,
        issues: MutableList<ValidationIssue>,
    ): WorkoverClearance? {
        val od = item.clearanceOuterDiameter
        // (name, hard limit, drift or null, basis of the hard limit)
        data class Passage(val name: String, val limit: Length, val drift: Length?, val basis: String)
        val passages = mutableListOf<Passage>()
        op.rigUp.bopStack.components.forEach { passages += Passage(it.name, it.bore, null, "BOP bore") }
        well.wellhead?.sections?.forEach { s -> s.bore?.let { passages += Passage(s.name, it, null, "wellhead bore") } }
        // Casing and liners the item passes through on its way to its deepest point.
        geometry.strings.filter { it.top < item.bottom && it.bottom > wellTop }.forEach { s ->
            passages += Passage(s.item.name, s.item.spec.innerDiameter, s.item.spec.driftDiameter, "ID")
        }
        // Open hole (not cased) between the well top and the item's bottom.
        if (item.bottom > wellTop) {
            for ((a, b) in geometry.breakpoints(wellTop, item.bottom).zipWithNext()) {
                val mid = Length((a.meters + b.meters) / 2)
                if (geometry.innermostStringAt(mid) != null) continue
                geometry.holeAt(mid)?.let { passages += Passage("${it.name} (open hole)", it.bitDiameter, null, "bit size") }
            }
        }
        if (passages.isEmpty()) return null
        // The governing passage is the smallest drift (else hard limit).
        val tight = passages.minBy { it.drift ?: it.limit }
        val governing = tight.drift ?: tight.limit
        val c = WorkoverClearance(item.id, item.name, od, tight.name, governing, if (tight.drift != null) "drift" else tight.basis, governing - od)
        val blocked = passages.filter { od >= it.limit }
        if (blocked.isNotEmpty()) {
            val b = blocked.minBy { it.limit }
            issues += ValidationIssue(Severity.ERROR, "WO_CLEARANCE", "'${item.name}' (${inch(od)}) does not pass the ${b.basis} of ${b.name} (${inch(b.limit)}).")
        } else {
            passages.filter { it.drift != null && od >= it.drift }.minByOrNull { it.drift!! }?.let { b ->
                issues += ValidationIssue(Severity.WARNING, "WO_CLEARANCE", "'${item.name}' (${inch(od)}) is not smaller than the drift of ${b.name} (${inch(b.drift!!)}): it may not pass.")
            }
        }
        return c
    }

    private fun cementPlug(
        geometry: WellboreGeometry,
        items: List<WorkoverItem>,
        stringSegments: List<VolumeSegment>,
        bottom: Length,
        op: WorkoverOperation,
        issues: MutableList<ValidationIssue>,
    ): CementPlugResult? {
        val length = op.plugLength
        if (length == null || length.meters <= 0.0) {
            issues += ValidationIssue(Severity.WARNING, "WO_PLUG_INPUTS", "Enter the plug length.")
            return null
        }
        val plugTop = bottom - length
        // Slurry = bore volume over the plug interval × (1 + excess).
        var bore = 0.0
        for ((a, b) in geometry.breakpoints(plugTop, bottom).zipWithNext()) {
            val d = boreCapacityAt(geometry, Length((a.meters + b.meters) / 2)) ?: run {
                issues += ValidationIssue(Severity.ERROR, "VOLUME_GEOMETRY_GAP", "No casing or hole recorded across the plug interval.")
                return null
            }
            bore += TubularGeometry.insideCapacity(d.first).cubicMetersPerMeter * (b - a).meters
        }
        val slurry = bore * (1.0 + op.slurryExcessFraction)
        // Height with pipe in: walk up from the bottom until the slurry is used.
        var remaining = slurry
        var z = bottom
        val stops = geometry.breakpoints(Length.ZERO.coerceAtLeastZero(), bottom, items.flatMap { listOf(it.top, it.bottom) }).filter { it < bottom }.sortedDescending()
        var lastCa = 0.0
        var lastCp = 0.0
        for (next in stops + listOf(Length.ZERO)) {
            if (remaining <= 0.0) break
            val mid = Length((z.meters + next.meters) / 2)
            val item = itemAt(items, mid)
            val d = boreCapacityAt(geometry, mid)?.first ?: break
            val od = item?.outerDiameter ?: Length.ZERO
            val id = item?.innerDiameter ?: Length.ZERO
            val ca = TubularGeometry.annularCapacity(d, od).cubicMetersPerMeter
            val cp = TubularGeometry.insideCapacity(id).cubicMetersPerMeter
            lastCa = ca; lastCp = cp
            val perM = ca + cp
            val segLen = (z - next).meters
            if (perM * segLen >= remaining) {
                z = Length(z.meters - remaining / perM)
                remaining = 0.0
            } else {
                remaining -= perM * segLen
                z = next
            }
        }
        if (remaining > 1e-9) {
            issues += ValidationIssue(Severity.ERROR, "WO_PLUG_TOO_LONG", "The slurry does not fit in the well above the plug bottom.")
            return null
        }
        val ahead = op.spacerAheadVolume?.cubicMeters ?: 0.0
        val behind = if (lastCa > 0.0) ahead * lastCp / lastCa else 0.0
        val pipeToTop = stringSegments.sumOf { s ->
            val a = s.topMd
            val b = minOf(s.bottomMd, z)
            if (b > a) s.capacity.cubicMetersPerMeter * (b - a).meters else 0.0
        }
        val displacement = pipeToTop - behind - (op.underDisplacementVolume?.cubicMeters ?: 0.0)
        if (displacement < 0.0) issues += ValidationIssue(Severity.ERROR, "WO_PLUG_DISPLACEMENT", "Displacement is negative: check spacer and under-displacement volumes.")
        if (plugTop < (geometry.strings.minOfOrNull { it.top } ?: plugTop)) {
            issues += ValidationIssue(Severity.WARNING, "WO_PLUG_TOP", "Plug top is above the top of the casing.")
        }
        issues += ValidationIssue(
            Severity.INFO, "WO_PLUG_METHOD",
            "Balanced-plug method: no contamination allowance, U-tube or pipe-pulling effects; hole volume to nominal ID / bit size.",
        )
        return CementPlugResult(plugTop, bottom, Volume(slurry), z, Volume(ahead), Volume(behind), Volume(displacement))
    }

    private fun acid(
        geometry: WellboreGeometry,
        ctx: WellGeometryContext,
        stringVolume: VolumeBreakdown,
        bottom: Length,
        tTop: Length?,
        tBottom: Length?,
        op: WorkoverOperation,
        issues: MutableList<ValidationIssue>,
    ): AcidResult? {
        val perLength = op.treatmentVolumePerLength
        if (tTop == null || tBottom == null || perLength == null) {
            issues += ValidationIssue(Severity.WARNING, "WO_ACID_INPUTS", "Enter the treatment interval and the volume per length.")
            return null
        }
        val treatment = perLength.cubicMetersPerMeter * (tBottom - tTop).meters
        val below = if (tTop > bottom) bore(geometry, bottom, tTop, "", issues).total.cubicMeters else 0.0
        val tvdTop = tvdBelowDatum(ctx, tTop, op.depthReference)
        val masp = op.fractureGradient?.let { fg ->
            op.pumpedFluidDensity?.let { rho -> Pressure(fg.pascalsPerMeter * tvdTop.meters - rho.kilogramsPerCubicMeter * G * tvdTop.meters) }
        }
        issues += ValidationIssue(Severity.INFO, "WO_ACID_FRICTION", "Maximum surface pressure excludes friction pressure and assumes a full column of the pumped fluid.")
        return AcidResult(Volume(treatment), Volume(stringVolume.total.cubicMeters + below), op.overflushVolume ?: Volume(0.0), masp)
    }

    private fun perforation(items: List<WorkoverItem>, tTop: Length?, tBottom: Length?, op: WorkoverOperation, issues: MutableList<ValidationIssue>): PerforationResult? {
        if (tTop == null || tBottom == null) {
            issues += ValidationIssue(Severity.WARNING, "WO_PERF_INPUTS", "Enter the perforation interval.")
            return null
        }
        val len = tBottom - tTop
        val shots = op.shotDensityPerMeter?.let { (it * len.meters).let { s -> kotlin.math.round(s).toInt() } }
        val runs = op.gunLengthPerRun?.takeIf { it.meters > 0.0 }?.let { ceil(len.meters / it.meters - 1e-9).toInt() }
        val guns = items.filter { it.toolType == WorkoverToolType.PERFORATING_GUN }
        val gunTop = guns.minOfOrNull { it.top }
        val gunBottom = guns.maxOfOrNull { it.bottom }
        if (gunTop != null && gunBottom != null && (gunTop > tTop + Length.meters(TOL) || gunBottom < tBottom - Length.meters(TOL))) {
            issues += ValidationIssue(
                Severity.INFO, "WO_GUN_COVERAGE",
                "Guns at this depth cover ${m(gunTop)}–${m(gunBottom)} m MD; the interval is ${m(tTop)}–${m(tBottom)} m MD.",
            )
        }
        return PerforationResult(len, shots, runs, gunTop, gunBottom)
    }

    private fun packer(geometry: WellboreGeometry, items: List<WorkoverItem>, annulus: VolumeBreakdown, tTop: Length?, issues: MutableList<ValidationIssue>): PackerResult? {
        val p = items.firstOrNull { it.toolType == WorkoverToolType.PACKER } ?: run {
            issues += ValidationIssue(Severity.WARNING, "WO_NO_PACKER", "Add a packer to the tools below the work string.")
            return null
        }
        val mid = Length((p.top.meters + p.bottom.meters) / 2)
        val host = geometry.innermostStringAt(mid)
        if (host == null) {
            issues += ValidationIssue(Severity.ERROR, "WO_PACKER_OPEN_HOLE", "Packer '${p.name}' is in open hole at ${m(p.top)} m MD.")
        }
        val boundaries = geometry.strings.flatMap { listOf(it.top, it.bottom) }.filter { it > p.top && it < p.bottom }
        if (boundaries.isNotEmpty()) {
            issues += ValidationIssue(Severity.WARNING, "WO_PACKER_ACROSS", "Packer '${p.name}' spans a liner top or casing shoe at ${m(boundaries.first())} m MD.")
        }
        val fluid = annulus.segments.sumOf { s ->
            val b = minOf(s.bottomMd, p.top)
            if (b > s.topMd) s.capacity.cubicMetersPerMeter * (b - s.topMd).meters else 0.0
        }
        val below = tTop?.takeIf { it > p.bottom }?.let { bore(geometry, p.bottom, it, "", issues).total }
        return PackerResult(p.top, p.bottom, host?.name, host?.spec?.innerDiameter, Volume(fluid), below)
    }

    private fun esp(ctx: WellGeometryContext, items: List<WorkoverItem>, tTop: Length?, op: WorkoverOperation, issues: MutableList<ValidationIssue>): EspResult? {
        val espTypes = setOf(WorkoverToolType.ESP_PUMP, WorkoverToolType.ESP_INTAKE, WorkoverToolType.ESP_SEAL, WorkoverToolType.ESP_MOTOR, WorkoverToolType.ESP_SENSOR)
        val parts = items.filter { it.toolType in espTypes }
        if (parts.isEmpty()) {
            issues += ValidationIssue(Severity.WARNING, "WO_NO_ESP", "Add the ESP components (pump, intake, seal, motor) to the tools.")
            return null
        }
        val intake = parts.firstOrNull { it.toolType == WorkoverToolType.ESP_INTAKE }?.top
        val motorBottom = parts.filter { it.toolType == WorkoverToolType.ESP_MOTOR }.maxOfOrNull { it.bottom }
        if (motorBottom != null && tTop != null && motorBottom > tTop) {
            issues += ValidationIssue(Severity.WARNING, "WO_ESP_BELOW_PERFS", "ESP motor ends below the top of the target interval: fluid may not flow past the motor (check shroud / cooling).")
        }
        val inc = intake?.let { ctx.positionAtCanonical(it).inclination }
        return EspResult(intake, intake?.let { tvdBelowDatum(ctx, it, op.depthReference) }, inc, motorBottom, parts.maxOf { it.outerDiameter })
    }

    private fun tvdBelowDatum(ctx: WellGeometryContext, canonicalMd: Length, datum: DepthReference): Length =
        ctx.tvd(ctx.canonicalMd(canonicalMd), datum).value

    private fun hydrostatic(rho: Density, tvd: Length) = Pressure(rho.kilogramsPerCubicMeter * G * tvd.meters)

    private fun Length.coerceAtLeastZero() = if (meters < 0.0) Length.ZERO else this

    companion object {
        const val VERSION = "1.0.0"
        private const val TOL = 1e-3
        private const val G = UnitDefinitions.STANDARD_GRAVITY_M_PER_S2
        private val CIRCULATING_TYPES = setOf(
            WorkoverOperationType.CEMENT_PLUG, WorkoverOperationType.ACIDIZING, WorkoverOperationType.CIRCULATION, WorkoverOperationType.WELL_KILL,
        )
        private fun m(l: Length) = String.format(java.util.Locale.US, "%.1f", l.meters)
        private fun inch(l: Length) = String.format(java.util.Locale.US, "%.3f in", l.inches)
        private fun psi(pa: Double) = String.format(java.util.Locale.US, "%.0f", pa / UnitDefinitions.PASCALS_PER_PSI)

        /** Capacity helper for the UI. */
        fun capacity(d: Length): VolumePerLength = TubularGeometry.insideCapacity(d)
    }
}
