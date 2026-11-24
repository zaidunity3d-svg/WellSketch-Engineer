package com.wellsketch.engineer.core.engineering.datum

import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.datum.WellDatums
import com.wellsketch.engineer.core.model.units.Length

/**
 * Converts depths between datums of one well.
 *
 * Every datum is resolved to an elevation above MSL. A depth below datum X of a point P is
 * `elev(X) − elev(P)`, so converting from datum S to datum T is
 *
 *     depth_T = depth_S + (elev(T) − elev(S))
 *
 * This is exact for vertical depths. For measured depths it relies on the hole between
 * the two datums being vertical, which holds for datums at or above the wellhead (RKB,
 * GL, operation datums on the surface stack). MD may therefore never be expressed relative
 * to MSL (enforced by [MeasuredDepth]).
 */
class DatumConverter(private val datums: WellDatums) {

    /** Elevation of [reference] above mean sea level. */
    fun elevationOf(reference: DepthReference): Length = when (reference) {
        DepthReference.Rkb -> datums.rkbElevation
        DepthReference.GroundLevel -> datums.groundLevelElevation
        DepthReference.MeanSeaLevel -> Length.ZERO
        is DepthReference.Operation -> datums.groundLevelElevation + reference.heightAboveGroundLevel
    }

    /** Offset to add to a depth on [from] to express it on [to]. */
    fun offset(from: DepthReference, to: DepthReference): Length = elevationOf(to) - elevationOf(from)

    fun convert(md: MeasuredDepth, to: DepthReference): MeasuredDepth {
        require(to !is DepthReference.MeanSeaLevel) { "Measured depth cannot be converted to MSL." }
        if (md.reference == to) return md
        return MeasuredDepth(md.value + offset(md.reference, to), to)
    }

    fun convert(tvd: TrueVerticalDepth, to: DepthReference): TrueVerticalDepth {
        if (tvd.reference == to) return tvd
        return TrueVerticalDepth(tvd.value + offset(tvd.reference, to), to)
    }
}
