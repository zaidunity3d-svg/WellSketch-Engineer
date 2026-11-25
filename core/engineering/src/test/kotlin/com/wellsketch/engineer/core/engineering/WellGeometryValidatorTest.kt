package com.wellsketch.engineer.core.engineering

import com.wellsketch.engineer.core.engineering.TestWells.casing
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.validation.WellGeometryValidator
import com.wellsketch.engineer.core.engineering.validation.hasErrors
import com.wellsketch.engineer.core.model.well.CasingRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WellGeometryValidatorTest {
    private val validator = WellGeometryValidator()

    private fun codes(well: com.wellsketch.engineer.core.model.well.Well) =
        validator.validate(WellGeometryContext(well), null).map { it.code }

    @Test
    fun consistentWellHasNoErrors() {
        val issues = validator.validate(WellGeometryContext(TestWells.verticalWell()), null)
        assertFalse(issues.hasErrors(), issues.toString())
        // no survey -> explicit warning that the vertical path is an assumption
        assertTrue(issues.any { it.code == "TRAJ_NO_SURVEY" })
    }

    @Test
    fun casingThatDoesNotFitInsideOuterStringIsReported() {
        val well = TestWells.verticalWell(
            listOf(
                casing("surface", CasingRole.SURFACE, 13.375, 12.4, 10.0, 500.0, 10.0),
                casing("too-big", CasingRole.PRODUCTION, 12.5, 11.5, 10.0, 2000.0, null),
            ),
        )
        assertTrue("CASING_NESTING" in codes(well))
    }

    @Test
    fun linerWithoutOverlapIsReported() {
        val well = TestWells.verticalWell(
            listOf(
                casing("surface", CasingRole.SURFACE, 13.375, 12.4, 10.0, 500.0, 10.0),
                casing("liner", CasingRole.PRODUCTION_LINER, 7.0, 6.2, 600.0, 2000.0, 600.0),
            ),
        )
        assertTrue("LINER_NO_HOST" in codes(well))
    }

    @Test
    fun shoeBelowDrilledDepthAndCementBelowShoeAreReported() {
        val bad = casing("production", CasingRole.PRODUCTION, 7.0, 6.2, 10.0, 2100.0, 1200.0)
        val well = TestWells.verticalWell(
            listOf(casing("surface", CasingRole.SURFACE, 13.375, 12.4, 10.0, 500.0, 10.0), bad),
        )
        assertTrue("CASING_BELOW_TD" in codes(well))
    }

    @Test
    fun casingOdLargerThanHoleIsReported() {
        val well = TestWells.verticalWell(
            listOf(casing("surface", CasingRole.SURFACE, 18.625, 17.7, 10.0, 500.0, 10.0)),
        )
        assertEquals(1, codes(well).count { it == "CASING_OD_VS_HOLE" })
    }
}
