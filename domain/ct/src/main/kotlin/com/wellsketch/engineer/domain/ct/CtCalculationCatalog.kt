package com.wellsketch.engineer.domain.ct

/**
 * CT calculations of the CT module. Volumes, displacement and pumping times are implemented
 * in [CtVolumeCalculator] (pending owner validation); hydrostatics use the Well
 * Calculations module. Forces and reach are not implemented: they need a reviewed model.
 */
object CtCalculationCatalog {
    data class PlannedCalculation(val id: String, val title: String, val description: String)

    val planned: List<PlannedCalculation> = listOf(
        PlannedCalculation("ct-internal-capacity", "CT internal capacity", "Internal volume of the CT string on the reel and in hole."),
        PlannedCalculation("ct-annular-volume", "CT annular volume", "Volume between CT and the conduit (tubing/casing/liner) to depth."),
        PlannedCalculation("ct-displacement", "CT displacement", "Volume displaced by running CT and BHA into the well."),
        PlannedCalculation("ct-pumping", "Pumping volumes and times", "Volumes and times to surface/bottom at a given pump rate."),
        PlannedCalculation("ct-hydrostatic", "Hydrostatic pressures", "Hydrostatic pressure of fluid columns using TVD."),
        PlannedCalculation("ct-forces", "CT forces and reach", "Weight, drag, buckling and lock-up (requires a reviewed model)."),
    )
}
