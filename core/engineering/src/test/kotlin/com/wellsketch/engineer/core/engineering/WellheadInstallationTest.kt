package com.wellsketch.engineer.core.engineering

import com.wellsketch.engineer.core.engineering.TestWells.m
import com.wellsketch.engineer.core.engineering.TestWells.synthetic
import com.wellsketch.engineer.core.engineering.surface.InstalledSectionsBasis
import com.wellsketch.engineer.core.engineering.surface.SurfaceStackupCalculator
import com.wellsketch.engineer.core.engineering.surface.WellheadInstallation
import com.wellsketch.engineer.core.model.surface.Cellar
import com.wellsketch.engineer.core.model.surface.DrillingBopComponent
import com.wellsketch.engineer.core.model.surface.DrillingBopComponentType
import com.wellsketch.engineer.core.model.surface.DrillingBopStack
import com.wellsketch.engineer.core.model.surface.DrillingRig
import com.wellsketch.engineer.core.model.surface.DrillingRigUp
import com.wellsketch.engineer.core.model.surface.EndConnection
import com.wellsketch.engineer.core.model.surface.OperatingState
import com.wellsketch.engineer.core.model.surface.Wellhead
import com.wellsketch.engineer.core.model.surface.WellheadSection
import com.wellsketch.engineer.core.model.surface.WellheadSectionType
import com.wellsketch.engineer.core.engineering.validation.ValidationIssue.Severity
import com.wellsketch.engineer.core.model.units.Length
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WellheadInstallationTest {

    private fun section(id: String, type: WellheadSectionType, top: String, bottom: String, h: Double) = WellheadSection(
        id = id, type = type, name = id, topConnection = EndConnection(top), bottomConnection = EndConnection(bottom),
        height = m(h), provenance = synthetic,
    )

    /** Top to bottom: tubing head spool 0.6, casing spool 0.8, casing head housing 0.7; base −2.0 m. */
    private val wellhead = Wellhead(
        id = "wh", name = "wh",
        sections = listOf(
            section("ths", WellheadSectionType.TUBING_HEAD_SPOOL, "WH-TOP", "B", 0.6),
            section("cs", WellheadSectionType.CASING_SPOOL, "B", "A", 0.8),
            section("chh", WellheadSectionType.CASING_HEAD_HOUSING, "A", "CSG", 0.7),
        ),
        baseRelativeToGroundLevel = m(-2.0),
        provenance = synthetic,
    )

    private fun rigUp(bopBottom: String, nippleUp: String? = null) = DrillingRigUp(
        rig = DrillingRig("rig", "rig", m(0.5), m(6.0), null, synthetic),
        bopStack = DrillingBopStack(
            "bop", "bop",
            listOf(
                DrillingBopComponent(
                    id = "ann", type = DrillingBopComponentType.ANNULAR_PREVENTER, name = "ann", bore = Length.inches(13.625),
                    topConnection = EndConnection("X"), bottomConnection = EndConnection("R"), height = m(1.3), provenance = synthetic,
                ),
                DrillingBopComponent(
                    id = "spool", type = DrillingBopComponentType.DRILLING_SPOOL, name = "spool", bore = Length.inches(13.625),
                    topConnection = EndConnection("R"), bottomConnection = EndConnection(bopBottom), height = m(0.6), provenance = synthetic,
                ),
            ),
            synthetic,
        ),
        nippledUpOnSectionId = nippleUp,
    )

    private fun ids(list: List<WellheadSection>) = list.map { it.id }

    @Test
    fun nonDrillingStatesUseTheWholeWellhead() {
        for (state in listOf(OperatingState.SurfaceNotDefined)) {
            val r = WellheadInstallation.installed(wellhead, state)
            assertEquals(listOf("ths", "cs", "chh"), ids(r.sections))
            assertTrue(r.notInstalled.isEmpty())
            assertEquals(InstalledSectionsBasis.ALL_SECTIONS, r.basis)
        }
    }

    @Test
    fun bopMatchingTheTubingHeadSpoolKeepsEverySection() {
        val r = WellheadInstallation.installed(wellhead, OperatingState.Drilling(rigUp("WH-TOP")))
        assertEquals(listOf("ths", "cs", "chh"), ids(r.sections))
        assertEquals(InstalledSectionsBasis.MATCHING_BOP_CONNECTION, r.basis)
    }

    @Test
    fun bopMatchingTheCasingSpoolLeavesTheTubingHeadSpoolOff() {
        val r = WellheadInstallation.installed(wellhead, OperatingState.Drilling(rigUp(" B ")))
        assertEquals(listOf("cs", "chh"), ids(r.sections))
        assertEquals(listOf("ths"), ids(r.notInstalled))
        assertEquals(InstalledSectionsBasis.MATCHING_BOP_CONNECTION, r.basis)
    }

    @Test
    fun noMatchingConnectionAssumesTheTubingHeadSpoolIsNotInstalledYet() {
        val r = WellheadInstallation.installed(wellhead, OperatingState.Drilling(rigUp("OTHER")))
        assertEquals(listOf("cs", "chh"), ids(r.sections))
        assertEquals(InstalledSectionsBasis.TUBING_HEAD_NOT_YET_INSTALLED, r.basis)
    }

    @Test
    fun enteredSectionWinsOverTheConnectionMatch() {
        val r = WellheadInstallation.installed(wellhead, OperatingState.Drilling(rigUp("WH-TOP", nippleUp = "chh")))
        assertEquals(listOf("chh"), ids(r.sections))
        assertEquals(listOf("ths", "cs"), ids(r.notInstalled))
        assertEquals(InstalledSectionsBasis.ENTERED, r.basis)
        assertNull(r.unknownNippleUpSectionId)
    }

    @Test
    fun unknownEnteredSectionFallsBackAndIsFlagged() {
        val r = WellheadInstallation.installed(wellhead, OperatingState.Drilling(rigUp("B", nippleUp = "gone")))
        assertEquals(listOf("cs", "chh"), ids(r.sections))
        assertEquals("gone", r.unknownNippleUpSectionId)
    }

    @Test
    fun sameFlangeOnTubingHeadAndCasingSpoolPrefersTheCasingSpoolWhileDrilling() {
        val same = wellhead.copy(
            sections = listOf(
                section("ths", WellheadSectionType.TUBING_HEAD_SPOOL, "F", "F", 0.6),
                section("cs", WellheadSectionType.CASING_SPOOL, "F", "A", 0.8),
                section("chh", WellheadSectionType.CASING_HEAD_HOUSING, "A", "CSG", 0.7),
            ),
        )
        assertEquals(listOf("cs", "chh"), ids(WellheadInstallation.installed(same, OperatingState.Drilling(rigUp("F"))).sections))
    }

    @Test
    fun wellheadWithoutTubingHeadAndNoMatchKeepsAllSections() {
        val noThs = wellhead.copy(sections = wellhead.sections.drop(1))
        val r = WellheadInstallation.installed(noThs, OperatingState.Drilling(rigUp("OTHER")))
        assertEquals(listOf("cs", "chh"), ids(r.sections))
        assertEquals(InstalledSectionsBasis.ALL_SECTIONS, r.basis)
    }

    @Test
    fun stackUpUsesOnlyTheInstalledSections() {
        val well = TestWells.verticalWell().copy(wellhead = wellhead, cellar = Cellar(m(2.5), m(2.4), synthetic))
        val r = SurfaceStackupCalculator().calculate(well, OperatingState.Drilling(rigUp("B")))
        // base −2.0 + 0.7 + 0.8 = −0.5 (tubing head spool not installed); BOP 0.6 + 1.3
        assertEquals(-0.5, r.wellheadTop!!.meters, 1e-12)
        assertEquals(1.4, r.drillingStackTop!!.meters, 1e-12)
        assertEquals(10.5, r.rkbToWellheadTop!!.meters, 1e-12) // RKB 10 m above GL
        assertEquals(4.6, r.clearanceUnderRotaryBeams!!.meters, 1e-12)
        assertTrue(r.issues.none { it.code == "BOP_WELLHEAD_CONNECTION_MISMATCH" })
        val info = r.issues.single { it.code == "WELLHEAD_SECTIONS_NOT_INSTALLED" }
        assertEquals(Severity.INFO, info.severity)
        assertEquals(listOf("ths"), info.subjectIds)

        val flagged = SurfaceStackupCalculator().calculate(well, OperatingState.Drilling(rigUp("B", nippleUp = "gone")))
        assertTrue(flagged.issues.any { it.code == "BOP_NIPPLE_UP_SECTION_NOT_FOUND" && it.severity == Severity.ERROR })

        // the wrong section entered: the mismatch is reported, not hidden
        val wrong = SurfaceStackupCalculator().calculate(well, OperatingState.Drilling(rigUp("B", nippleUp = "chh")))
        assertTrue(wrong.issues.any { it.code == "BOP_WELLHEAD_CONNECTION_MISMATCH" })
        assertEquals(-1.3, wrong.wellheadTop!!.meters, 1e-12)
    }
}
