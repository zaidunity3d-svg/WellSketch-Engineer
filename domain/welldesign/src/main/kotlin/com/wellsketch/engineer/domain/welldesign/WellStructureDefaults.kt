package com.wellsketch.engineer.domain.welldesign

import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.catalog.TubularKind
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingRole
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.Well

/**
 * Starting values for "+ Add casing / liner" and "+ Install tubing". They are only a place to
 * start editing (the user then picks the real type, size and depths); they never invent
 * dimensions, since every size comes from a catalogue record.
 */
object WellStructureDefaults {

    /** Order in which "+ Add casing / liner" proposes string types on an empty well. */
    val STRING_SEQUENCE: List<CasingRole> = listOf(
        CasingRole.CONDUCTOR, CasingRole.SURFACE, CasingRole.INTERMEDIATE, CasingRole.PRODUCTION, CasingRole.PRODUCTION_LINER,
    )

    /** Usual type of the string run inside a string of type [innermost] (null: empty well). */
    fun nextRole(innermost: CasingRole?): CasingRole = when (innermost) {
        null -> CasingRole.CONDUCTOR
        CasingRole.CONDUCTOR -> CasingRole.SURFACE
        CasingRole.SURFACE -> CasingRole.INTERMEDIATE
        CasingRole.INTERMEDIATE -> CasingRole.PRODUCTION
        CasingRole.INTERMEDIATE_LINER, CasingRole.PRODUCTION, CasingRole.PRODUCTION_LINER, CasingRole.TIEBACK -> CasingRole.PRODUCTION_LINER
    }

    /**
     * Adds the next string inside the innermost one, with starting values the user then edits:
     * - type: the usual next type ([nextRole]);
     * - size: the largest catalogue OD that passes the innermost string's drift (else its ID)
     *   and is smaller than the bit of the first hole section drilled below that string's shoe;
     *   lightest catalogue weight of that OD;
     * - top: GL (a liner hangs 150 m above the previous shoe);
     * - shoe: bottom of the deepest hole section the string can reach, i.e. every hole section
     *   from the top down has a bit larger than the OD (else 500 m below the previous shoe).
     * Coupling OD and annular clearance are not checked here (flagged in the gap report).
     * All depths are compared in the well's canonical MD datum, so strings entered from GL
     * and from RKB mix correctly.
     */
    fun addNextString(well: Well): EditOutcome {
        val ctx = WellGeometryContext(well)
        val ref = ctx.canonicalReference
        val holes = well.holeSections.sortedBy { ctx.canonical(it.top).meters }
        val innermost = well.casingStrings.minByOrNull { it.spec.outerDiameter.meters }
        val outerShoe = innermost?.let { ctx.canonical(it.shoe.depth) }
        val role = nextRole(innermost?.role)
        val passLimit = innermost?.spec?.let { it.driftDiameter ?: it.innerDiameter }?.inches ?: Double.MAX_VALUE
        val nextHole = holes.firstOrNull { outerShoe == null || ctx.canonical(it.bottom) > outerShoe + Length.meters(0.001) }
        val fitLimit = minOf(passLimit, nextHole?.bitDiameter?.inches ?: Double.MAX_VALUE)
        val od = TubularCatalog.outerDiameters(TubularKind.CASING).firstOrNull { it < fitLimit - 1e-6 }
            ?: return EditOutcome.Rejected(
                "No catalogue casing fits inside ${innermost?.name ?: "the hole"}" + (nextHole?.let { " and the ${it.name}" } ?: ""),
            )
        val spec = TubularCatalog.weights(TubularKind.CASING, od).first().toSpec(null, null)
        val gl = ctx.canonical(WellStructureEdits.groundLevelMd(well))
        val top = if (role.isLiner && outerShoe != null) maxOf(gl, outerShoe - Length.meters(LINER_LAP_M)) else gl
        val reachable = holes
            .filter { ctx.canonical(it.bottom) > top }
            .takeWhile { it.bitDiameter.inches > od + 1e-6 }
            .lastOrNull()
            ?.let { ctx.canonical(it.bottom) }
        val shoe = reachable?.takeIf { it > top + Length.meters(10.0) && (outerShoe == null || it > outerShoe) }
            ?: (maxOf(top, outerShoe ?: top) + Length.meters(500.0))
        return WellStructureEdits.addCasingString(well, role, spec, MeasuredDepth(top, ref), MeasuredDepth(shoe, ref))
    }

    /** Installs 3-1/2 in tubing (lightest catalogue weight) from GL to 100 m above the deepest shoe. */
    fun installDefaultTubing(well: Well, state: CompletionState): EditOutcome {
        val ctx = WellGeometryContext(well)
        val ref = ctx.canonicalReference
        val spec = TubularCatalog.weights(TubularKind.TUBING, 3.5).first().toSpec(null, null)
        val gl = ctx.canonical(WellStructureEdits.groundLevelMd(well))
        val deepestShoe = well.casingStrings.maxOfOrNull { ctx.canonical(it.shoe.depth) }
        val eot = deepestShoe?.takeIf { it > gl + Length.meters(200.0) }?.let { it - Length.meters(100.0) }
            ?: (gl + Length.meters(1000.0))
        return WellStructureEdits.setTubing(well, state, spec, MeasuredDepth(gl, ref), MeasuredDepth(eot, ref))
    }

    /**
     * Sets a liner's running string (drill pipe or tubing) to the largest catalogue size that
     * passes the host casing at the liner top with 0.25 in clearance: drill pipe is checked by
     * its tool-joint OD, tubing by its body OD (coupling OD is not in the catalogue). The host is
     * the innermost other string that spans the liner top. The user can change the pick freely.
     */
    fun setDefaultRunningString(well: Well, linerId: String, kind: TubularKind): EditOutcome {
        val ctx = WellGeometryContext(well)
        val liner = well.casingStrings.firstOrNull { it.id == linerId } ?: return EditOutcome.Rejected("Unknown casing string")
        if (!liner.isLiner) return EditOutcome.Rejected("A running string applies to liners only")
        if (kind == TubularKind.CASING) return EditOutcome.Rejected("The running string is drill pipe or tubing")
        val linerTop = ctx.canonical(liner.top)
        val host = well.casingStrings
            .filter { it.id != liner.id && ctx.canonical(it.top) <= linerTop && ctx.canonical(it.shoe.depth) >= linerTop }
            .minByOrNull { it.spec.innerDiameter.meters }
        val passLimit = host?.spec?.let { it.driftDiameter ?: it.innerDiameter }?.inches ?: Double.MAX_VALUE
        val candidates = TubularCatalog.entries.filter { it.kind == kind }
        val pick = candidates
            .filter { (it.toolJointOdIn ?: it.outerDiameterIn) < passLimit - RUNNING_CLEARANCE_IN }
            .maxByOrNull { it.outerDiameterIn * 100 + it.nominalWeightLbFt / 100 }
            ?: return EditOutcome.Rejected("No catalogue ${if (kind == TubularKind.DRILL_PIPE) "drill pipe" else "tubing"} passes ${host?.name ?: "the host casing"}")
        return WellStructureEdits.setRunningString(well, linerId, pick.toSpec(null, null))
    }

    private const val LINER_LAP_M = 150.0
    private const val RUNNING_CLEARANCE_IN = 0.25
}
