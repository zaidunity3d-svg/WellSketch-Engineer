package com.wellsketch.engineer.domain.wellcalc

/**
 * Lifecycle of an engineering calculation. A calculation may only be offered to users once
 * it is VALIDATED: formula, references, units, validity range and test cases approved.
 */
enum class CalculationStatus(val label: String) {
    AWAITING_SPECIFICATION_REVIEW("Awaiting specification review"),
    SPECIFIED("Specification approved, not implemented"),
    IMPLEMENTED_PENDING_VALIDATION("Implemented, pending validation"),
    VALIDATED("Validated"),
}

enum class CalculationCategory(val label: String) {
    GEOMETRY("Tubular geometry"),
    CAPACITY("Capacities"),
    DISPLACEMENT("Displacements"),
    PRESSURE("Hydrostatics & pressure"),
    CEMENTING("Cementing"),
}

/**
 * Specification placeholder for a planned calculation. Inputs/outputs are listed by name
 * only; formulas are intentionally absent until the owner reviews the specification.
 */
data class CalculationSpecification(
    val id: String,
    val title: String,
    val category: CalculationCategory,
    val purpose: String,
    val plannedInputs: List<String>,
    val plannedOutputs: List<String>,
    val status: CalculationStatus = CalculationStatus.AWAITING_SPECIFICATION_REVIEW,
    /** Reference sources to be agreed with the owner (none assumed). */
    val references: List<String> = emptyList(),
    val version: String? = null,
)

/**
 * Contract every calculation implements once specified. Calculations are versioned and live
 * in pure Kotlin so they can be unit-tested independently from the UI.
 */
interface EngineeringCalculation<in I, out O> {
    val specification: CalculationSpecification
    val version: String
    fun calculate(input: I): CalculationResult<O>
}

data class CalculationResult<out O>(
    val value: O,
    val calculationId: String,
    val version: String,
    val warnings: List<String> = emptyList(),
)

/** Planned Well Calculations module content (no formulas implemented in this milestone). */
object WellCalculationCatalog {
    val specifications: List<CalculationSpecification> = listOf(
        CalculationSpecification(
            "tubular-geometry", "Tubular cross-sections", CalculationCategory.GEOMETRY,
            "Cross-sectional areas of tubulars from their stated OD and ID.",
            listOf("Outer diameter", "Inner diameter"), listOf("Bore area", "Steel area"),
        ),
        CalculationSpecification(
            "inside-capacity", "Inside capacity", CalculationCategory.CAPACITY,
            "Internal capacity of casing, liner, tubing or drill pipe over an MD interval.",
            listOf("Inner diameter", "MD interval"), listOf("Capacity per length", "Interval volume"),
        ),
        CalculationSpecification(
            "annular-capacity", "Annular capacity", CalculationCategory.CAPACITY,
            "Capacity between an inner string OD and the outer string ID or open hole.",
            listOf("Inner string OD", "Outer ID / hole size", "MD interval", "Hole enlargement (if any)"),
            listOf("Annular capacity per length", "Interval volume"),
        ),
        CalculationSpecification(
            "displacement", "Pipe displacement", CalculationCategory.DISPLACEMENT,
            "Open-end and closed-end displacement of strings run into the well.",
            listOf("OD", "ID", "Length", "Open/closed end"), listOf("Displacement volume"),
        ),
        CalculationSpecification(
            "hydrostatic", "Hydrostatic pressure", CalculationCategory.PRESSURE,
            "Hydrostatic pressure of one or more fluid columns using true vertical depth.",
            listOf("Fluid densities", "Column tops/bottoms (TVD)", "Datum"), listOf("Pressure at depth", "Gradient"),
        ),
        CalculationSpecification(
            "cement-volumes", "Cement volumes", CalculationCategory.CEMENTING,
            "Slurry volumes for annular intervals, shoe track and agreed excess.",
            listOf("Annular geometry", "TOC", "Shoe track length", "Excess policy"), listOf("Slurry volume by interval"),
        ),
        CalculationSpecification(
            "cement-displacement", "Cement displacement", CalculationCategory.CEMENTING,
            "Displacement volume to bump the plug at the float collar.",
            listOf("String ID", "Float collar depth"), listOf("Displacement volume"),
        ),
    )
}
