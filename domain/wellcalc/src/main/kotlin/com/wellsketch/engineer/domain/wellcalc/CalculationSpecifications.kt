package com.wellsketch.engineer.domain.wellcalc

import com.wellsketch.engineer.core.engineering.volumes.TubularGeometry

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

/**
 * Well Calculations module content. Implemented calculations are marked
 * IMPLEMENTED_PENDING_VALIDATION with their version and formula basis; they become
 * VALIDATED only after the owner reviews the specification, references and test cases.
 */
object WellCalculationCatalog {
    private const val GEOMETRY = "Exact circle area A = π/4 · D² on the stated diameters"
    private const val UNITS = "Exact unit definitions: 1 in = 0.0254 m, 1 ft = 0.3048 m, 1 US gal = 231 in³, 1 bbl = 42 US gal"

    val specifications: List<CalculationSpecification> = listOf(
        CalculationSpecification(
            "tubular-geometry", "Tubular cross-sections", CalculationCategory.GEOMETRY,
            "Wall thickness and cross-sectional areas of tubulars from their stated OD and ID.",
            listOf("Outer diameter", "Inner diameter"), listOf("Wall (OD − ID)/2", "Bore area", "Steel area"),
            status = CalculationStatus.IMPLEMENTED_PENDING_VALIDATION, version = TubularGeometry.VERSION,
            references = listOf(GEOMETRY, UNITS),
        ),
        CalculationSpecification(
            "inside-capacity", "Inside capacity", CalculationCategory.CAPACITY,
            "Internal capacity of casing, liner or tubing, and inside volume of the flow path over an MD interval (split at every geometry change).",
            listOf("Inner diameter", "MD interval"), listOf("Capacity per length", "Interval volume"),
            status = CalculationStatus.IMPLEMENTED_PENDING_VALIDATION, version = WellVolumeCalculator.VERSION,
            references = listOf("Capacity = π/4 · ID²", GEOMETRY, UNITS),
        ),
        CalculationSpecification(
            "annular-capacity", "Annular capacity", CalculationCategory.CAPACITY,
            "Capacity between an inner string OD and the outer string ID or open hole (nominal bit size).",
            listOf("Inner string OD", "Outer ID / bit size", "MD interval"),
            listOf("Annular capacity per length", "Interval volume"),
            status = CalculationStatus.IMPLEMENTED_PENDING_VALIDATION, version = WellVolumeCalculator.VERSION,
            references = listOf("Annular capacity = π/4 · (D² − d²)", GEOMETRY, UNITS),
        ),
        CalculationSpecification(
            "displacement", "Pipe displacement", CalculationCategory.DISPLACEMENT,
            "Open-end (steel) and closed-end displacement of strings run into the well (plain pipe body).",
            listOf("OD", "ID", "Length"), listOf("Open-end displacement", "Closed-end displacement"),
            status = CalculationStatus.IMPLEMENTED_PENDING_VALIDATION, version = TubularGeometry.VERSION,
            references = listOf("Open end = π/4 · (OD² − ID²); closed end = π/4 · OD²", GEOMETRY),
        ),
        CalculationSpecification(
            "hydrostatic", "Hydrostatic pressure", CalculationCategory.PRESSURE,
            "Static pressure of one or more fluid columns using the true vertical height from the survey.",
            listOf("Fluid densities", "Column tops/bottoms (MD)", "Surface pressure"),
            listOf("Pressure at depth", "Gradient", "Equivalent density"),
            status = CalculationStatus.IMPLEMENTED_PENDING_VALIDATION, version = HydrostaticCalculator.VERSION,
            references = listOf("P = P_surface + Σ ρ · g · ΔTVD, g = 9.80665 m/s² (standard gravity)", "ΔTVD from the minimum-curvature trajectory", UNITS),
        ),
        CalculationSpecification(
            "cement-volumes", "Cement volumes", CalculationCategory.CEMENTING,
            "Slurry volumes for the annulus (cased and open hole, with excess), shoe track and optional rathole.",
            listOf("Annular geometry", "TOC", "Float collar", "Excess policy"), listOf("Slurry volume by interval"),
            status = CalculationStatus.IMPLEMENTED_PENDING_VALIDATION, version = CementVolumeCalculator.VERSION,
            references = listOf("Annulus π/4 · (D² − d²) × (1 + excess)", "Shoe track π/4 · ID² × (shoe − float collar)", GEOMETRY),
        ),
        CalculationSpecification(
            "cement-displacement", "Cement displacement", CalculationCategory.CEMENTING,
            "Displacement volume from the string top to the float collar.",
            listOf("String ID", "Float collar depth"), listOf("Displacement volume"),
            status = CalculationStatus.IMPLEMENTED_PENDING_VALIDATION, version = CementVolumeCalculator.VERSION,
            references = listOf("Displacement = π/4 · ID² × (float collar − string top)", GEOMETRY),
        ),
    )
}
