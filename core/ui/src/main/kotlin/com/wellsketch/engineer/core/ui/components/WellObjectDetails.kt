package com.wellsketch.engineer.core.ui.components

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.WellObject
import com.wellsketch.engineer.core.ui.format.UnitFormatter

/** Title, rows and provenance describing a well object for [DetailCard]. */
data class ObjectDetails(val title: String, val rows: List<Pair<String, String>>, val provenance: DataProvenance?)

/** Builds detail rows from actual stored geometry; missing values are shown as "Not provided". */
fun wellObjectDetails(obj: WellObject, ctx: WellGeometryContext, f: UnitFormatter): ObjectDetails {
    val ref = ctx.canonicalReference.shortLabel
    fun md(v: MeasuredDepth) = "${f.depth(ctx.canonical(v))} MD $ref"
    fun dia(v: Length?) = v?.let(f::diameter) ?: "Not provided"
    return when (obj) {
        is WellObject.Casing -> {
            val s = obj.value
            ObjectDetails(
                s.name,
                listOfNotNull(
                    "Role" to s.role.label,
                    "OD" to dia(s.spec.outerDiameter),
                    "ID" to dia(s.spec.innerDiameter),
                    "Drift" to dia(s.spec.driftDiameter),
                    "Weight" to (s.spec.nominalLinearMass?.let { "${UnitFormatter.number(it.poundsPerFoot, 2)} lb/ft" } ?: "Not provided"),
                    "Grade" to (s.spec.grade ?: "Not provided"),
                    "Connection" to (s.spec.connection ?: "Not provided"),
                    "Top" to md(s.top),
                    "Shoe" to "${md(s.shoe.depth)} (${s.shoe.type.label})",
                    "Shoe TVD" to "${f.depth(ctx.tvdBelowGroundLevel(ctx.canonical(s.shoe.depth)))} below GL",
                    s.cement?.let { "Cement" to "${md(it.top)} to ${md(it.bottom)} – ${it.status.label}" },
                    s.runningString?.let { "Running string" to "${f.nominalSize(it.outerDiameter)} (ID ${f.diameter(it.innerDiameter)})" },
                    "Spec data" to s.spec.provenance.status.label,
                ),
                s.provenance,
            )
        }
        is WellObject.Hole -> ObjectDetails(
            obj.value.name,
            listOfNotNull(
                "Bit size (nominal)" to dia(obj.value.bitDiameter),
                obj.value.averageDiameter?.let { "Average hole (caliper)" to dia(it) },
                "Top" to md(obj.value.top),
                "Bottom" to md(obj.value.bottom),
            ),
            obj.value.provenance,
        )
        is WellObject.Tubing -> ObjectDetails(
            obj.value.name,
            listOf(
                "OD" to dia(obj.value.spec.outerDiameter),
                "ID" to dia(obj.value.spec.innerDiameter),
                "Drift" to dia(obj.value.spec.driftDiameter),
                "Hanger" to md(obj.value.hangerDepth),
                "End of tubing" to md(obj.value.endOfTubing),
            ),
            obj.value.provenance,
        )
        is WellObject.Component -> ObjectDetails(
            obj.value.name,
            listOf(
                "Type" to obj.value.type.label,
                "Mounting" to obj.value.mounting.label,
                "Top" to md(obj.value.top),
                "Length" to f.depth(obj.value.length),
                "OD" to dia(obj.value.outerDiameter),
                "Minimum ID" to if (obj.value.isFullBoreBlocking) "Blocks bore" else dia(obj.value.minimumInnerDiameter),
                "Library entry" to (obj.value.catalogItemId ?: "Not linked"),
            ),
            obj.value.provenance,
        )
        is WellObject.Perforation -> ObjectDetails(
            "Perforation interval",
            listOf("Top" to md(obj.value.top), "Bottom" to md(obj.value.bottom), "Status" to obj.value.status.label),
            obj.value.provenance,
        )
        is WellObject.Formation -> ObjectDetails(
            obj.value.name,
            listOf("Lithology" to obj.value.lithology.label, "Top" to "${f.depth(ctx.tvdBelowGroundLevel(obj.value.top))} TVD below GL"),
            obj.value.provenance,
        )
    }
}

const val BREAKDOWN_PENDING_NOTE =
    "Detailed engineering view and component breakdown: planned. Breakdowns will only use owner-approved reference images."
