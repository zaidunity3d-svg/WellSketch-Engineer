package com.wellsketch.engineer.core.model.units

import kotlinx.serialization.Serializable

/**
 * Display/input unit system preference. Storage is always SI; this only selects how
 * values are presented and parsed in the UI.
 */
@Serializable
enum class UnitSystem(val label: String) {
    /** ft for depths, in for diameters, psi, bbl, ppg, lb/ft. */
    OILFIELD("Oilfield (ft, in, psi)"),

    /** m for depths, mm for diameters, bar, m³, kg/m³, kg/m. */
    METRIC("Metric (m, mm, bar)"),

    /**
     * Mixed field units common on metric-depth locations: m for depths, in for diameters,
     * psi, bbl, SG for fluid density, lb/ft for tubular weight.
     */
    FIELD_METRIC("Field metric (m, in, psi, bbl)"),
}
