package com.wellsketch.engineer.core.schematic

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatermarkTest {
    private val perPx = Branding.WATERMARK_TEXT.length * 0.55

    private fun fits(p: WatermarkPlacement, w: Double, h: Double, tw: Double, th: Double = 1.2): Boolean {
        val r = abs(p.angleDegrees) * PI / 180
        val bw = p.textSizePx * (tw * cos(r) + th * sin(r))
        val bh = p.textSizePx * (tw * sin(r) + th * cos(r))
        return bw <= w * 0.70 + 1e-9 && bh <= h * 0.70 + 1e-9
    }

    @Test
    fun watermarkTextNamesTheAppAndTheDesigner() {
        assertEquals("WellSketch Engineer · Designed by Zaid Fawzi", Branding.WATERMARK_TEXT)
        assertTrue(Branding.WATERMARK_ALPHA in 0.03f..0.12f, "must stay faint so the sketch is legible")
    }

    @Test
    fun portraitPhoneSketchGetsACentredDiagonalThatFits() {
        val p = Watermarks.place(1080.0, 1900.0, perPx)!!
        assertEquals(540.0, p.centerX, 1e-9)
        assertEquals(950.0, p.centerY, 1e-9)
        assertEquals(-60.0, p.angleDegrees, 1e-9) // steep diagonal is capped at 60°
        assertTrue(fits(p, 1080.0, 1900.0, perPx))
    }

    @Test
    fun landscapeAreaFollowsItsDiagonal() {
        val p = Watermarks.place(2000.0, 1000.0, perPx)!!
        assertEquals(-Math.toDegrees(Math.atan2(1000.0, 2000.0)), p.angleDegrees, 1e-9)
        assertTrue(fits(p, 2000.0, 1000.0, perPx))
    }

    @Test
    fun alwaysInsideTheAreaForManySizes() {
        for (w in listOf(320.0, 720.0, 1080.0, 1600.0, 2560.0)) {
            for (h in listOf(300.0, 900.0, 1900.0, 2800.0)) {
                val p = Watermarks.place(w, h, perPx) ?: continue
                assertTrue(fits(p, w, h, perPx), "w=$w h=$h")
                assertTrue(abs(p.angleDegrees) in 20.0..60.0)
            }
        }
    }

    @Test
    fun tinyOrInvalidAreasGetNoWatermark() {
        assertNull(Watermarks.place(60.0, 40.0, perPx))
        assertNull(Watermarks.place(0.0, 500.0, perPx))
        assertNull(Watermarks.place(500.0, 500.0, 0.0))
        assertNull(Watermarks.place(Double.NaN, 500.0, perPx))
    }
}
