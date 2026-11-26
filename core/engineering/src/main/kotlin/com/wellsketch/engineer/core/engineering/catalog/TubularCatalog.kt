package com.wellsketch.engineer.core.engineering.catalog

import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.provenance.DataStatus
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.LinearMass
import com.wellsketch.engineer.core.model.well.TubularSpec
import kotlin.math.abs

enum class TubularKind(val label: String) { CASING("Casing / liner"), TUBING("Tubing") }

/**
 * One OD + nominal weight record of the tubular catalogue, in inches and lb/ft as published.
 * Dimensions only: grade and connection are user selections and their ratings are not part
 * of this record. [driftIn] is null when the source does not list a drift.
 */
data class TubularCatalogEntry(
    val id: String,
    val kind: TubularKind,
    val outerDiameterIn: Double,
    val nominalWeightLbFt: Double,
    val wallIn: Double,
    val innerDiameterIn: Double,
    val driftIn: Double?,
    /** Tubing end finish (NU non-upset, EU external upset); null for casing. */
    val endFinish: String?,
    val sourceUrl: String,
) {
    val outerDiameter: Length get() = Length.inches(outerDiameterIn)
    val innerDiameter: Length get() = Length.inches(innerDiameterIn)
    val wallThickness: Length get() = Length.inches(wallIn)
    val drift: Length? get() = driftIn?.let { Length.inches(it) }
    val nominalWeight: LinearMass get() = LinearMass.poundsPerFoot(nominalWeightLbFt)

    /** e.g. "26 lb/ft" or "6.5 lb/ft EU". */
    val weightLabel: String get() = "${plain(nominalWeightLbFt)} lb/ft" + (endFinish?.let { " $it" } ?: "")

    val provenance: DataProvenance
        get() = DataProvenance(
            status = DataStatus.UNVERIFIED,
            source = sourceUrl,
            reference = TubularCatalog.REVISION,
            notes = "Public API 5CT dimensional reference; OEM verification required. Grade/connection ratings not included.",
        )

    /** Tubular specification for a string built from this record and the user's grade/connection. */
    fun toSpec(grade: String?, connection: String?): TubularSpec = TubularSpec(
        outerDiameter = outerDiameter,
        innerDiameter = innerDiameter,
        wallThickness = wallThickness,
        driftDiameter = drift,
        nominalLinearMass = nominalWeight,
        grade = grade?.takeIf { it.isNotBlank() },
        connection = connection?.takeIf { it.isNotBlank() } ?: endFinish,
        catalogItemId = id,
        provenance = provenance,
    )

    private companion object {
        fun plain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    }
}

/**
 * Tubular catalogue: the 31 public API 5CT dimensional records of the owner's HTML prototype
 * v0.9 (casing 4-1/2 to 20 in, tubing 2-3/8 to 4-1/2 in), each with its source. These are
 * reference dimensions, not OEM certificates (status UNVERIFIED until the owner verifies them).
 * The list is meant to grow from the owner's approved sheets.
 */
object TubularCatalog {
    const val SOURCE_ZC = "https://zc-steel-pipe.com/specs/api-5ct/"
    const val SOURCE_YIIP = "https://www.yiip.com.tr/en/product-document/52/api-5ct/22"
    const val REVISION = "Reference API 5CT dimensional listing; not OEM release (as used in HTML prototype v0.9)"

    /** Steel grades offered for selection (not a dimension driver). */
    val GRADES: List<String> = listOf("H40", "J55", "K55", "N80", "L80", "P110", "C90", "T95", "Q125")
    val CASING_CONNECTIONS: List<String> = listOf("STC", "LTC", "BTC", "Premium (OEM check)", "Other (OEM check)")
    val TUBING_CONNECTIONS: List<String> = listOf("NU", "EU", "Premium (OEM check)", "Other (OEM check)")

    private fun row(
        id: String, kind: TubularKind, od: Double, weight: Double, wall: Double, idIn: Double, drift: Double?, finish: String?, source: String,
    ) = TubularCatalogEntry(id, kind, od, weight, wall, idIn, drift, finish, source)

    val entries: List<TubularCatalogEntry> = listOf(
        row("c-20-94-api", TubularKind.CASING, 20.0, 94.0, 0.4382, 19.122, 18.9358, null, SOURCE_ZC),
        row("c-13p375-48-api", TubularKind.CASING, 13.375, 48.0, 0.3299, 12.715, 12.5587, null, SOURCE_ZC),
        row("c-13p375-54p5-api", TubularKind.CASING, 13.375, 54.5, 0.3799, 12.615, 12.4587, null, SOURCE_ZC),
        row("c-13p375-68-api", TubularKind.CASING, 13.375, 68.0, 0.4799, 12.415, 12.2587, null, SOURCE_ZC),
        row("c-9p625-36-api", TubularKind.CASING, 9.625, 36.0, 0.352, 8.9213, 8.765, null, SOURCE_ZC),
        row("c-9p625-40-api", TubularKind.CASING, 9.625, 40.0, 0.3949, 8.8346, 8.6791, null, SOURCE_ZC),
        row("c-9p625-43p5-api", TubularKind.CASING, 9.625, 43.5, 0.435, 8.7559, 8.5988, null, SOURCE_ZC),
        row("c-9p625-47-api", TubularKind.CASING, 9.625, 47.0, 0.472, 8.6811, 8.5252, null, SOURCE_ZC),
        row("c-9p625-53p5-api", TubularKind.CASING, 9.625, 53.5, 0.5449, 8.5354, 8.3791, null, SOURCE_ZC),
        row("c-7-20-api", TubularKind.CASING, 7.0, 20.0, 0.272, 6.4559, 6.3307, null, SOURCE_ZC),
        row("c-7-23-api", TubularKind.CASING, 7.0, 23.0, 0.3169, 6.3661, 6.2409, null, SOURCE_ZC),
        row("c-7-26-api", TubularKind.CASING, 7.0, 26.0, 0.3618, 6.2764, 6.1512, null, SOURCE_ZC),
        row("c-7-29-api", TubularKind.CASING, 7.0, 29.0, 0.4079, 6.1843, 6.0591, null, SOURCE_ZC),
        row("c-7-32-api", TubularKind.CASING, 7.0, 32.0, 0.4531, 6.0937, 5.9685, null, SOURCE_ZC),
        row("c-5p5-17-api", TubularKind.CASING, 5.5, 17.0, 0.3039, 4.8921, 4.7669, null, SOURCE_ZC),
        row("c-5p5-20-api", TubularKind.CASING, 5.5, 20.0, 0.361, 4.778, 4.6528, null, SOURCE_ZC),
        row("c-5-18-api", TubularKind.CASING, 5.0, 18.0, 0.3618, 4.2764, 4.1512, null, SOURCE_ZC),
        row("c-4p5-13p5-api", TubularKind.CASING, 4.5, 13.5, 0.2902, 3.9197, 3.7945, null, SOURCE_ZC),
        row("t-2p375-4p6-nu", TubularKind.TUBING, 2.375, 4.6, 0.1902, 1.9945, 1.9008, "NU", SOURCE_YIIP),
        row("t-2p875-6p4-nu", TubularKind.TUBING, 2.875, 6.4, 0.2169, 2.4409, 2.3472, "NU", SOURCE_YIIP),
        row("t-2p875-6p5-eu", TubularKind.TUBING, 2.875, 6.5, 0.2169, 2.4409, 2.3472, "EU", SOURCE_ZC),
        row("t-2p875-7p8-nu", TubularKind.TUBING, 2.875, 7.8, 0.276, 2.3228, null, "NU", SOURCE_ZC),
        row("t-2p875-7p9-eu", TubularKind.TUBING, 2.875, 7.9, 0.276, 2.3228, null, "EU", SOURCE_ZC),
        row("t-3p5-7p7-nu", TubularKind.TUBING, 3.5, 7.7, 0.2161, 3.0677, 2.9425, "NU", SOURCE_YIIP),
        row("t-3p5-9p2-nu", TubularKind.TUBING, 3.5, 9.2, 0.2539, 2.9921, 2.8669, "NU", SOURCE_YIIP),
        row("t-3p5-9p3-eu", TubularKind.TUBING, 3.5, 9.3, 0.2539, 2.9921, null, "EU", SOURCE_ZC),
        row("t-3p5-10p2-nu", TubularKind.TUBING, 3.5, 10.2, 0.289, 2.922, null, "NU", SOURCE_ZC),
        row("t-3p5-12p7-nu", TubularKind.TUBING, 3.5, 12.7, 0.3748, 2.7504, null, "NU", SOURCE_ZC),
        row("t-3p5-12p95-eu", TubularKind.TUBING, 3.5, 12.95, 0.3748, 2.7504, null, "EU", SOURCE_ZC),
        row("t-4p5-12p6-nu", TubularKind.TUBING, 4.5, 12.6, 0.2709, 3.9583, 3.8331, "NU", SOURCE_YIIP),
        row("t-4p5-12p75-eu", TubularKind.TUBING, 4.5, 12.75, 0.2709, 3.9583, null, "EU", SOURCE_ZC),
    )

    fun byId(id: String?): TubularCatalogEntry? = id?.let { k -> entries.firstOrNull { it.id == k } }

    /** Available outside diameters for a kind, largest first. */
    fun outerDiameters(kind: TubularKind): List<Double> =
        entries.filter { it.kind == kind }.map { it.outerDiameterIn }.distinct().sortedDescending()

    /** Records of one OD (nominal weights), lightest first. */
    fun weights(kind: TubularKind, outerDiameterIn: Double): List<TubularCatalogEntry> =
        entries.filter { it.kind == kind && abs(it.outerDiameterIn - outerDiameterIn) < 1e-6 }.sortedBy { it.nominalWeightLbFt }

    /** The catalogue record a specification came from (by id, else by matching OD and ID). */
    fun match(spec: TubularSpec, kind: TubularKind): TubularCatalogEntry? =
        byId(spec.catalogItemId) ?: entries.firstOrNull {
            it.kind == kind &&
                abs(it.outerDiameter.meters - spec.outerDiameter.meters) < 1e-5 &&
                abs(it.innerDiameter.meters - spec.innerDiameter.meters) < Length.inches(0.002).meters
        }
}
