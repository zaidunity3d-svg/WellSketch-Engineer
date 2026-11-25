package com.wellsketch.engineer.core.model

import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.TubularSpec
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DepthModelTest {

    @Test
    fun measuredDepthCannotBeReferencedToMsl() {
        assertFailsWith<IllegalArgumentException> {
            MeasuredDepth(Length.meters(100.0), DepthReference.MeanSeaLevel)
        }
    }

    @Test
    fun tvdMayBeReferencedToMsl() {
        TrueVerticalDepth.msl(Length.meters(100.0))
    }

    @Test
    fun depthsOnDifferentDatumsCannotBeCompared() {
        val a = MeasuredDepth.rkb(Length.meters(100.0))
        val b = MeasuredDepth.gl(Length.meters(90.0))
        assertFailsWith<IllegalArgumentException> { a.compareTo(b) }
        assertTrue(a > MeasuredDepth.rkb(Length.meters(50.0)))
    }

    @Test
    fun tubularSpecRejectsInconsistentGeometry() {
        assertFailsWith<IllegalArgumentException> {
            TubularSpec(
                outerDiameter = Length.inches(7.0),
                innerDiameter = Length.inches(7.5),
                provenance = DataProvenance.syntheticDemo(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TubularSpec(
                outerDiameter = Length.inches(7.0),
                innerDiameter = Length.inches(6.0),
                driftDiameter = Length.inches(6.1),
                provenance = DataProvenance.syntheticDemo(),
            )
        }
    }
}
