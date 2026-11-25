package com.wellsketch.engineer.core.engineering.summary

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CompletionComponentType
import com.wellsketch.engineer.core.model.well.CompletionState
import com.wellsketch.engineer.core.model.well.PerforationStatus
import com.wellsketch.engineer.core.model.well.Well

enum class KeyDepthKind(val label: String) {
    CASING_SHOE("Casing shoe"),
    LINER_TOP("Liner top"),
    PACKER("Packer"),
    PLUG("Plug"),
    END_OF_TUBING("End of tubing"),
    PERFORATIONS("Perforations"),
    TOTAL_DEPTH("Total depth"),
}

/**
 * One row of the read-only "Key depths" summary. [md] and [tvd] are on the same datum (the
 * well's canonical MD reference, normally RKB), so the two columns are never mixed; the
 * bottom values are set for intervals (perforations).
 */
data class KeyDepth(
    val kind: KeyDepthKind,
    val sourceId: String?,
    val name: String,
    val md: Length,
    val tvd: Length,
    val mdBottom: Length? = null,
    val tvdBottom: Length? = null,
    /** Outer diameter of casing and liner items, from the string specification. */
    val outerDiameter: Length? = null,
    val perforationStatus: PerforationStatus? = null,
)

/**
 * Builds the key depths of a well, sorted by measured depth, from entered data only: casing
 * and liner shoes, liner tops, packers and plugs, end of tubing and perforation intervals of
 * the selected completion state, and total depth (deepest hole-section bottom).
 */
object KeyDepths {

    fun build(well: Well, state: CompletionState?, ctx: WellGeometryContext = WellGeometryContext(well)): List<KeyDepth> {
        val ref = ctx.canonicalReference
        fun tvdOf(md: Length): Length = ctx.tvd(ctx.canonicalMd(md), ref).value
        val out = mutableListOf<KeyDepth>()
        for (c in well.casingStrings) {
            val od = c.spec.outerDiameter
            val shoe = ctx.canonical(c.shoe.depth)
            out += KeyDepth(KeyDepthKind.CASING_SHOE, c.id, c.name, shoe, tvdOf(shoe), outerDiameter = od)
            if (c.isLiner) {
                val top = ctx.canonical(c.top)
                out += KeyDepth(KeyDepthKind.LINER_TOP, c.id, c.name, top, tvdOf(top), outerDiameter = od)
            }
        }
        val completion = state?.let { well.completion(it) }
        if (completion != null) {
            for (k in completion.components) {
                val kind = when (k.type) {
                    CompletionComponentType.PACKER -> KeyDepthKind.PACKER
                    CompletionComponentType.BRIDGE_PLUG, CompletionComponentType.CEMENT_RETAINER -> KeyDepthKind.PLUG
                    else -> null
                } ?: continue
                val top = ctx.canonical(k.top)
                out += KeyDepth(kind, k.id, k.name, top, tvdOf(top))
            }
            completion.tubingString?.let { t ->
                val eot = ctx.canonical(t.endOfTubing)
                out += KeyDepth(KeyDepthKind.END_OF_TUBING, t.id, t.name, eot, tvdOf(eot))
            }
            for (p in completion.perforations) {
                val top = ctx.canonical(p.top)
                val bottom = ctx.canonical(p.bottom)
                out += KeyDepth(
                    KeyDepthKind.PERFORATIONS, p.id, "Perforations", top, tvdOf(top),
                    mdBottom = bottom, tvdBottom = tvdOf(bottom), perforationStatus = p.status,
                )
            }
        }
        well.holeSections.maxByOrNull { ctx.canonical(it.bottom).meters }?.let { h ->
            val td = ctx.canonical(h.bottom)
            out += KeyDepth(KeyDepthKind.TOTAL_DEPTH, h.id, "Total depth", td, tvdOf(td))
        }
        return out.sortedWith(compareBy<KeyDepth> { it.md.meters }.thenBy { it.kind.ordinal })
    }
}
