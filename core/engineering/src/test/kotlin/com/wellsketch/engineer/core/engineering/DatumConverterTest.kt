package com.wellsketch.engineer.core.engineering

import com.wellsketch.engineer.core.engineering.datum.DatumConverter
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.datum.WellDatums
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatumConverterTest {
    // Synthetic test datums: GL 300 m above MSL, RKB 9.5 m above GL.
    private val datums = WellDatums(
        rkbElevation = Length.meters(309.5),
        groundLevelElevation = Length.meters(300.0),
        provenance = DataProvenance.syntheticDemo(),
    )
    private val converter = DatumConverter(datums)

    @Test
    fun tvdRkbToGlAndMsl() {
        val tvdRkb = TrueVerticalDepth.rkb(Length.meters(1000.0))
        assertEquals(990.5, converter.convert(tvdRkb, DepthReference.GroundLevel).value.meters, 1e-9)
        // TVDSS = TVD(RKB) − RKB elevation above MSL
        assertEquals(690.5, converter.convert(tvdRkb, DepthReference.MeanSeaLevel).value.meters, 1e-9)
    }

    @Test
    fun conversionsRoundTrip() {
        val tvdMsl = TrueVerticalDepth.msl(Length.meters(1234.5))
        val back = converter.convert(converter.convert(tvdMsl, DepthReference.Rkb), DepthReference.MeanSeaLevel)
        assertEquals(1234.5, back.value.meters, 1e-9)
    }

    @Test
    fun groundLevelIsNegativeMdOnRkbScaleInverse() {
        // GL lies 9.5 m below RKB: MD(RKB) of GL is +9.5 m; RKB on the GL scale is −9.5 m.
        assertEquals(9.5, converter.convert(MeasuredDepth.gl(Length.ZERO), DepthReference.Rkb).value.meters, 1e-9)
        assertEquals(-9.5, converter.convert(MeasuredDepth.rkb(Length.ZERO), DepthReference.GroundLevel).value.meters, 1e-9)
    }

    @Test
    fun operationDatumConvertsWithExplicitOffset() {
        // CT counter zero 6.0 m above GL (below RKB, which is 9.5 m above GL).
        val ctZero = DepthReference.Operation("op", "CT zero", Length.meters(6.0))
        val ctDepth = MeasuredDepth(Length.meters(2000.0), ctZero)
        // MD(RKB) = MD(op) + (elev(RKB) − elev(op)) = 2000 + (309.5 − 306.0)
        assertEquals(2003.5, converter.convert(ctDepth, DepthReference.Rkb).value.meters, 1e-9)
        assertEquals(1994.0, converter.convert(ctDepth, DepthReference.GroundLevel).value.meters, 1e-9)
    }

    @Test
    fun measuredDepthCannotBeConvertedToMsl() {
        assertFailsWith<IllegalArgumentException> {
            converter.convert(MeasuredDepth.rkb(Length.meters(10.0)), DepthReference.MeanSeaLevel)
        }
    }
}
