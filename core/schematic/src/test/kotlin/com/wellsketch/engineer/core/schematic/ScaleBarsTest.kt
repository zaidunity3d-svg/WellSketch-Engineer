package com.wellsketch.engineer.core.schematic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScaleBarsTest {

    @Test
    fun picksLongestNiceMetricBarThatFits() {
        assertEquals(ScaleBar(1.0, 2, "1 m"), ScaleBars.choose(pxPerMeter = 100.0, maxPx = 180.0))
        assertEquals(ScaleBar(2.0, 2, "2 m"), ScaleBars.choose(pxPerMeter = 100.0, maxPx = 250.0))
        assertEquals(ScaleBar(5.0, 5, "5 m"), ScaleBars.choose(pxPerMeter = 30.0, maxPx = 160.0))
        assertEquals(ScaleBar(10.0, 2, "10 m"), ScaleBars.choose(pxPerMeter = 10.0, maxPx = 160.0))
    }

    @Test
    fun exactFitIsAccepted() {
        assertEquals("2 m", ScaleBars.choose(pxPerMeter = 50.0, maxPx = 100.0)?.label)
    }

    @Test
    fun smallBarsKeepDecimals() {
        val bar = ScaleBars.choose(pxPerMeter = 1000.0, maxPx = 300.0)!!
        assertEquals("0.2 m", bar.label)
        assertEquals(0.2, bar.meters, 1e-12)
    }

    @Test
    fun feetBarIsConvertedToMetres() {
        val bar = ScaleBars.choose(pxPerMeter = 100.0, maxPx = 180.0, feet = true)!!
        assertEquals("5 ft", bar.label)
        assertEquals(5 * 0.3048, bar.meters, 1e-12)
        assertTrue(bar.meters * 100.0 <= 180.0)
    }

    @Test
    fun barNeverExceedsTheRoom() {
        for (ppm in listOf(3.7, 12.0, 41.3, 93.5, 210.0, 777.0)) {
            for (room in listOf(40.0, 97.0, 160.0, 333.0)) {
                for (feet in listOf(false, true)) {
                    val bar = ScaleBars.choose(ppm, room, feet)!!
                    assertTrue(bar.meters * ppm <= room * (1 + 1e-9), "ppm=$ppm room=$room feet=$feet")
                    assertTrue(bar.meters * ppm > room / 2.5 - 1e-9, "bar too short: ppm=$ppm room=$room feet=$feet")
                }
            }
        }
    }

    @Test
    fun unusableInputsGiveNoBar() {
        assertNull(ScaleBars.choose(0.0, 100.0))
        assertNull(ScaleBars.choose(100.0, 0.0))
        assertNull(ScaleBars.choose(Double.NaN, 100.0))
        assertNull(ScaleBars.choose(Double.POSITIVE_INFINITY, 100.0))
    }
}
