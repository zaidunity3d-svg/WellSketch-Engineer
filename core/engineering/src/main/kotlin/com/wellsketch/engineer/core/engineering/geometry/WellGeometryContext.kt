package com.wellsketch.engineer.core.engineering.geometry

import com.wellsketch.engineer.core.engineering.datum.DatumConverter
import com.wellsketch.engineer.core.engineering.trajectory.Trajectory
import com.wellsketch.engineer.core.engineering.trajectory.TrajectoryPoint
import com.wellsketch.engineer.core.engineering.trajectory.TrajectorySource
import com.wellsketch.engineer.core.model.datum.DepthReference
import com.wellsketch.engineer.core.model.datum.MeasuredDepth
import com.wellsketch.engineer.core.model.datum.TrueVerticalDepth
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.well.Well

/**
 * Resolves depths of one well consistently: converts any MD to the well's canonical MD
 * reference (the trajectory reference) and maps MD to TVD on any datum via the trajectory.
 *
 * All geometry consumers (schematic, CT placement, validators) use this class so that
 * datum handling is implemented and tested in exactly one place.
 */
class WellGeometryContext(val well: Well) {
    val converter = DatumConverter(well.datums)

    val trajectory: Trajectory = well.activeSurvey?.let(Trajectory::fromSurvey)
        ?: Trajectory.assumedVertical(well.datums.primaryMdReference)

    /** MD reference in which all internal geometry is expressed. */
    val canonicalReference: DepthReference get() = trajectory.reference

    val trajectorySource: TrajectorySource get() = trajectory.source

    /** Converts [md] to the canonical reference and returns its value. */
    fun canonical(md: MeasuredDepth): Length = converter.convert(md, canonicalReference).value

    fun canonicalMd(value: Length): MeasuredDepth = MeasuredDepth(value, canonicalReference)

    fun positionAt(md: MeasuredDepth): TrajectoryPoint = trajectory.positionAt(canonical(md))

    /** Position at a canonical MD value. */
    fun positionAtCanonical(md: Length): TrajectoryPoint = trajectory.positionAt(md)

    fun tvd(md: MeasuredDepth, on: DepthReference): TrueVerticalDepth {
        val tvdOnTrajectoryRef = TrueVerticalDepth(trajectory.tvdAt(canonical(md)), canonicalReference)
        return converter.convert(tvdOnTrajectoryRef, on)
    }

    /** TVD below ground level of a canonical MD value (the schematic's vertical axis). */
    fun tvdBelowGroundLevel(canonicalMd: Length): Length {
        val tvd = TrueVerticalDepth(trajectory.tvdAt(canonicalMd), canonicalReference)
        return converter.convert(tvd, DepthReference.GroundLevel).value
    }

    /** Converts a TVD on any datum to TVD below ground level. */
    fun tvdBelowGroundLevel(tvd: TrueVerticalDepth): Length =
        converter.convert(tvd, DepthReference.GroundLevel).value

    /** Canonical MD of ground level (e.g. RKB height above GL when the reference is RKB). */
    val groundLevelCanonicalMd: Length
        get() = canonical(MeasuredDepth.gl(Length.ZERO))
}
