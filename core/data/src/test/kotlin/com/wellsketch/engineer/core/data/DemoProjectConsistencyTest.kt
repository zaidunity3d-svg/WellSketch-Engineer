package com.wellsketch.engineer.core.data

import com.wellsketch.engineer.core.data.demo.SyntheticDemoProjects
import com.wellsketch.engineer.core.engineering.geometry.WellGeometryContext
import com.wellsketch.engineer.core.engineering.trajectory.TrajectorySource
import com.wellsketch.engineer.core.engineering.validation.WellGeometryValidator
import com.wellsketch.engineer.core.engineering.validation.errors
import com.wellsketch.engineer.core.model.units.Length
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The demo fixtures must themselves be geometrically consistent. */
class DemoProjectConsistencyTest {

    @Test
    fun demoWellsHaveNoGeometryErrors() {
        val validator = WellGeometryValidator()
        for (project in SyntheticDemoProjects.all()) {
            val ctx = WellGeometryContext(project.well)
            assertEquals(TrajectorySource.SURVEY, ctx.trajectorySource)
            project.well.completions.forEach { completion ->
                val errors = validator.validate(ctx, completion).errors()
                assertTrue(errors.isEmpty(), "${project.id}/${completion.state}: $errors")
            }
        }
    }

    @Test
    fun horizontalDemoReachesNinetyDegrees() {
        val ctx = WellGeometryContext(SyntheticDemoProjects.horizontal().well)
        val p = ctx.positionAtCanonical(Length.meters(2500.0))
        assertEquals(90.0, p.inclination.degrees, 1e-6)
    }

    @Test
    fun sShapedDemoReturnsToVertical() {
        val ctx = WellGeometryContext(SyntheticDemoProjects.sShaped().well)
        assertEquals(0.0, ctx.positionAtCanonical(Length.meters(2500.0)).inclination.degrees, 1e-6)
    }
}
