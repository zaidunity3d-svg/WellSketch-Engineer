package com.wellsketch.engineer.core.engineering.volumes

import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.CasingString
import com.wellsketch.engineer.core.model.well.Completion
import com.wellsketch.engineer.core.model.well.CompletionComponent
import com.wellsketch.engineer.core.model.well.CompletionComponentType
import com.wellsketch.engineer.core.model.well.HoleSection
import com.wellsketch.engineer.core.model.well.TubingString

/** Which diameter a volume segment was computed from. */
enum class DiameterBasis(val label: String) {
    /** Inside diameter stated in the tubular's data (catalog or measurement). */
    STATED_ID("Stated ID"),

    /** Nominal bit size: open-hole volumes ignore washout/enlargement unless a caliper is entered. */
    BIT_SIZE("Nominal bit size"),
}

/** The bore that contains fluid at a depth, or the outer wall of an annulus. */
sealed interface Bore {
    val diameter: Length
    val basis: DiameterBasis
    val sourceId: String
    val name: String

    data class Tubing(val tubing: TubingString) : Bore {
        override val diameter get() = tubing.spec.innerDiameter
        override val basis get() = DiameterBasis.STATED_ID
        override val sourceId get() = tubing.id
        override val name get() = tubing.name
    }

    data class Casing(val string: CasingString) : Bore {
        override val diameter get() = string.spec.innerDiameter
        override val basis get() = DiameterBasis.STATED_ID
        override val sourceId get() = string.id
        override val name get() = string.name
    }

    data class OpenHole(val hole: HoleSection) : Bore {
        override val diameter get() = hole.bitDiameter
        override val basis get() = DiameterBasis.BIT_SIZE
        override val sourceId get() = hole.id
        override val name get() = hole.name
    }
}

/**
 * Resolves, at any measured depth, which tubulars and hole are present: the bore that
 * holds fluid (tubing, then the innermost casing/liner, then open hole) and the outer
 * boundary of the annulus outside any string. All MDs are canonical (see
 * [WellGeometryContext]); intervals that are inverted because of incomplete input are
 * ignored here and reported by the geometry validator.
 *
 * Shared by the well-volume, cementing and CT calculations so that every calculation uses
 * the same geometry rules as the schematic.
 */
class WellboreGeometry(val ctx: WellGeometryContext, val completion: Completion?) {

    data class Span<T>(val item: T, val top: Length, val bottom: Length) {
        operator fun contains(md: Length) = md >= top && md <= bottom
    }

    val strings: List<Span<CasingString>> = ctx.well.casingStrings
        .map { Span(it, ctx.canonical(it.top), ctx.canonical(it.shoe.depth)) }
        .filter { it.bottom >= it.top }

    val holes: List<Span<HoleSection>> = ctx.well.holeSections
        .map { Span(it, ctx.canonical(it.top), ctx.canonical(it.bottom)) }
        .filter { it.bottom >= it.top }

    /** Installed production tubing (hanger to end of tubing), if any. */
    val tubing: Span<TubingString>? = completion?.tubingString
        ?.let { Span(it, ctx.canonical(it.hangerDepth), ctx.canonical(it.endOfTubing)) }
        ?.takeIf { it.bottom >= it.top }

    val components: List<Span<CompletionComponent>> = completion?.components.orEmpty()
        .map { val top = ctx.canonical(it.top); Span(it, top, top + it.length) }

    /** Deepest recorded depth: last hole section bottom or casing shoe. */
    val deepestMd: Length? = (holes.map { it.bottom } + strings.map { it.bottom }).maxOrNull()

    /** Top of the shallowest packer on the tubing (the bottom of the tubing × casing annulus). */
    val packerTop: Length? = components
        .filter { it.item.type == CompletionComponentType.PACKER }
        .minOfOrNull { it.top }

    /** Top of the shallowest component that blocks the full bore (e.g. a bridge plug). */
    val blockingTop: Span<CompletionComponent>? = components
        .filter { it.item.isFullBoreBlocking }
        .minByOrNull { it.top }

    /** Hole section at [md]; at a boundary between sections the smaller (deeper) one wins. */
    fun holeAt(md: Length): HoleSection? =
        holes.filter { md in it }.minByOrNull { it.item.bitDiameter }?.item

    /** Innermost (smallest ID) casing or liner covering [md]. */
    fun innermostStringAt(md: Length): CasingString? =
        strings.filter { md in it }.minByOrNull { it.item.spec.innerDiameter }?.item

    /** Bore holding fluid at [md]: production tubing, else innermost casing/liner, else open hole. */
    fun boreAt(md: Length): Bore? {
        tubing?.takeIf { md in it }?.let { return Bore.Tubing(it.item) }
        innermostStringAt(md)?.let { return Bore.Casing(it) }
        return holeAt(md)?.let { Bore.OpenHole(it) }
    }

    /**
     * Outer boundary of the annulus outside a pipe of outside diameter [pipeOd] at [md]: the
     * innermost casing/liner whose ID is larger than [pipeOd] (excluding [exceptStringId]),
     * otherwise the open hole.
     */
    fun annulusBoundaryAt(md: Length, pipeOd: Length, exceptStringId: String? = null): Bore? {
        val enclosing = strings
            .filter { it.item.id != exceptStringId && md in it && it.item.spec.innerDiameter > pipeOd }
            .minByOrNull { it.item.spec.innerDiameter }
        if (enclosing != null) return Bore.Casing(enclosing.item)
        return holeAt(md)?.let { Bore.OpenHole(it) }
    }

    /**
     * Every depth inside [top]..[bottom] where the geometry may change: string tops and
     * shoes, hole-section boundaries, tubing hanger and end, component ends, plus [extra].
     * Sorted, without near-duplicates, always including [top] and [bottom].
     */
    fun breakpoints(top: Length, bottom: Length, extra: List<Length> = emptyList()): List<Length> {
        if (bottom < top) return emptyList()
        val all = buildList {
            add(top); add(bottom); addAll(extra)
            strings.forEach { add(it.top); add(it.bottom) }
            holes.forEach { add(it.top); add(it.bottom) }
            tubing?.let { add(it.top); add(it.bottom) }
            components.forEach { add(it.top); add(it.bottom) }
        }
        val sorted = all.filter { it >= top && it <= bottom }.sorted()
        val out = mutableListOf<Length>()
        for (d in sorted) if (out.isEmpty() || (d - out.last()).meters > BREAK_TOLERANCE_M) out += d
        if (out.last() != bottom) {
            if ((bottom - out.last()).meters <= BREAK_TOLERANCE_M) out[out.lastIndex] = bottom else out += bottom
        }
        return out
    }

    companion object {
        /** Depths closer than this are merged (1 mm). */
        const val BREAK_TOLERANCE_M = 1e-3
    }
}
