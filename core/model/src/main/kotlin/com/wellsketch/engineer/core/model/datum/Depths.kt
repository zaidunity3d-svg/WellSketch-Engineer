package com.wellsketch.engineer.core.model.datum

import com.wellsketch.engineer.core.model.units.Length
import kotlinx.serialization.Serializable

/**
 * Measured depth: distance along the wellbore from [reference], positive downward.
 * Measured depths cannot be referenced to MSL (see [DepthReference.MeanSeaLevel]).
 */
@Serializable
data class MeasuredDepth(
    val value: Length,
    val reference: DepthReference,
) : Comparable<MeasuredDepth> {
    init {
        require(reference !is DepthReference.MeanSeaLevel) {
            "Measured depth cannot be referenced to MSL; use RKB, GL or an operation datum."
        }
    }

    /**
     * Comparison is only meaningful for depths on the same reference. Convert first with
     * the datum converter in :core:engineering.
     */
    override fun compareTo(other: MeasuredDepth): Int {
        require(reference == other.reference) {
            "Cannot compare MD on ${reference.shortLabel} with MD on ${other.reference.shortLabel}; convert first."
        }
        return value.compareTo(other.value)
    }

    override fun toString(): String = "MD ${value.meters} m ${reference.shortLabel}"

    companion object {
        fun rkb(value: Length) = MeasuredDepth(value, DepthReference.Rkb)
        fun gl(value: Length) = MeasuredDepth(value, DepthReference.GroundLevel)
    }
}

/** True vertical depth below [reference], positive downward. */
@Serializable
data class TrueVerticalDepth(
    val value: Length,
    val reference: DepthReference,
) : Comparable<TrueVerticalDepth> {
    override fun compareTo(other: TrueVerticalDepth): Int {
        require(reference == other.reference) {
            "Cannot compare TVD on ${reference.shortLabel} with TVD on ${other.reference.shortLabel}; convert first."
        }
        return value.compareTo(other.value)
    }

    override fun toString(): String = "TVD ${value.meters} m ${reference.shortLabel}"

    companion object {
        fun rkb(value: Length) = TrueVerticalDepth(value, DepthReference.Rkb)
        fun gl(value: Length) = TrueVerticalDepth(value, DepthReference.GroundLevel)
        fun msl(value: Length) = TrueVerticalDepth(value, DepthReference.MeanSeaLevel)
    }
}
