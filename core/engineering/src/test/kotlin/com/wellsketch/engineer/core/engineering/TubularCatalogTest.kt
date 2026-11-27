package com.wellsketch.engineer.core.engineering

import com.wellsketch.engineer.core.engineering.catalog.TubularCatalog
import com.wellsketch.engineer.core.engineering.catalog.TubularKind
import com.wellsketch.engineer.core.model.provenance.DataStatus
import com.wellsketch.engineer.core.model.units.Length
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Consistency checks of every catalogue record. They do not replace OEM verification; they
 * catch typos (wrong wall for a weight, ID not matching OD and wall, wrong drift allowance).
 */
class TubularCatalogTest {
    private val all = TubularCatalog.entries

    @Test
    fun catalogueMatchesTheHtmlPrototypeRecords() {
        assertEquals(46, all.size)
        assertEquals(18, all.count { it.kind == TubularKind.CASING })
        assertEquals(13, all.count { it.kind == TubularKind.TUBING })
        assertEquals(15, all.count { it.kind == TubularKind.DRILL_PIPE })
        assertEquals(all.size, all.map { it.id }.toSet().size, "ids are unique")
        assertEquals(listOf(20.0, 13.375, 9.625, 7.0, 5.5, 5.0, 4.5), TubularCatalog.outerDiameters(TubularKind.CASING))
        assertEquals(listOf(4.5, 3.5, 2.875, 2.375), TubularCatalog.outerDiameters(TubularKind.TUBING))
        assertTrue(all.all { it.sourceUrl.startsWith("https://") })
    }

    @Test
    fun insideDiameterEqualsOdMinusTwoWalls() {
        for (e in all) assertEquals(e.outerDiameterIn - 2 * e.wallIn, e.innerDiameterIn, 0.003, e.id)
    }

    @Test
    fun driftFollowsTheApiAllowanceForItsSize() {
        for (e in all) {
            val drift = e.driftIn ?: continue
            val allowance = when (e.kind) {
                TubularKind.CASING -> when {
                    e.outerDiameterIn < 9.625 -> 1.0 / 8
                    e.outerDiameterIn <= 13.375 -> 5.0 / 32
                    else -> 3.0 / 16
                }
                TubularKind.TUBING -> if (e.outerDiameterIn <= 2.875) 3.0 / 32 else 1.0 / 8
                TubularKind.DRILL_PIPE -> continue // API 5DP pipe body lists no drift
            }
            assertEquals(e.innerDiameterIn - allowance, drift, 0.002, e.id)
        }
    }

    @Test
    fun nominalWeightIsConsistentWithThePlainEndSteelWeight() {
        // API plain-end weight: w = 10.68 (D - t) t lb/ft. Nominal weight includes couplings/upsets.
        for (e in all) {
            val plainEnd = 10.68 * (e.outerDiameterIn - e.wallIn) * e.wallIn
            val ratio = e.nominalWeightLbFt / plainEnd
            // Drill-pipe nominal (designation) weight is a size label that sits above the plain-end weight.
            val range = if (e.kind == TubularKind.DRILL_PIPE) 1.0..1.15 else 0.98..1.08
            assertTrue(ratio in range, "${e.id}: nominal ${e.nominalWeightLbFt} vs plain-end ${"%.2f".format(plainEnd)}")
        }
    }

    @Test
    fun drillPipeRowsCarryAPlausibleToolJoint() {
        val dps = all.filter { it.kind == TubularKind.DRILL_PIPE }
        assertEquals(listOf(5.5, 5.0, 4.5, 4.0, 3.5, 2.875, 2.375), TubularCatalog.outerDiameters(TubularKind.DRILL_PIPE))
        for (e in dps) {
            assertTrue(e.toolJointOdIn!! > e.outerDiameterIn, "${e.id}: tool joint OD above pipe OD")
            assertTrue(e.toolJointIdIn!! < e.innerDiameterIn, "${e.id}: tool joint ID below pipe ID")
            assertEquals(TubularCatalog.SOURCE_DP, e.sourceUrl)
        }
        val spec = TubularCatalog.byId("dp-3p5-13p3-eu")!!.toSpec("G-105", null)
        assertEquals("NC38", spec.connection)
        assertEquals(2.764, spec.innerDiameter.inches, 1e-9)
    }

    @Test
    fun weightsOfOneOdAreListedLightestFirst() {
        val sevens = TubularCatalog.weights(TubularKind.CASING, 7.0)
        assertEquals(listOf(20.0, 23.0, 26.0, 29.0, 32.0), sevens.map { it.nominalWeightLbFt })
        assertEquals(listOf("6.5 lb/ft EU", "6.4 lb/ft NU", "7.8 lb/ft NU", "7.9 lb/ft EU").sorted(), TubularCatalog.weights(TubularKind.TUBING, 2.875).map { it.weightLabel }.sorted())
    }

    @Test
    fun specCarriesDimensionsProvenanceAndUserSelections() {
        val e = TubularCatalog.weights(TubularKind.CASING, 9.625).single { it.nominalWeightLbFt == 47.0 }
        val spec = e.toSpec("L80", "BTC")
        assertEquals(Length.inches(9.625).meters, spec.outerDiameter.meters, 1e-12)
        assertEquals(Length.inches(8.6811).meters, spec.innerDiameter.meters, 1e-12)
        assertEquals(Length.inches(8.5252).meters, spec.driftDiameter!!.meters, 1e-12)
        assertEquals(47.0, spec.nominalLinearMass!!.poundsPerFoot, 1e-9)
        assertEquals("L80", spec.grade)
        assertEquals("BTC", spec.connection)
        assertEquals(e.id, spec.catalogItemId)
        assertEquals(DataStatus.UNVERIFIED, spec.provenance.status)
        assertTrue(!spec.provenance.isEngineeringGrade)
        // tubing without an explicit connection keeps its end finish
        val t = TubularCatalog.weights(TubularKind.TUBING, 3.5).first { it.endFinish == "EU" }
        assertEquals("EU", t.toSpec(null, null).connection)
        assertNull(t.toSpec(" ", null).grade)
    }

    @Test
    fun specFromAnotherSourceIsMatchedByOdAndId() {
        val e = TubularCatalog.byId("c-7-26-api") ?: TubularCatalog.weights(TubularKind.CASING, 7.0).single { it.nominalWeightLbFt == 26.0 }
        val manual = e.toSpec(null, null).copy(catalogItemId = null)
        assertEquals(e.id, TubularCatalog.match(manual, TubularKind.CASING)!!.id)
        assertNull(TubularCatalog.match(manual.copy(innerDiameter = Length.inches(6.0), driftDiameter = null), TubularKind.CASING))
    }
}
