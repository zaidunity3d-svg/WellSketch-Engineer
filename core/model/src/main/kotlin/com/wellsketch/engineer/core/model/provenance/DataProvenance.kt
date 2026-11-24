package com.wellsketch.engineer.core.model.provenance

import kotlinx.serialization.Serializable

/**
 * Where a piece of engineering data came from and how far it can be trusted.
 *
 * Every catalog item, dimension set and demo fixture carries a provenance so that
 * synthetic demonstration values can never be mistaken for verified engineering data,
 * and nominal catalog values are never silently treated as measured values.
 */
@Serializable
data class DataProvenance(
    val status: DataStatus,
    /** Human-readable source, e.g. "User entry", "Manufacturer catalog", "Synthetic demo". */
    val source: String,
    /** Document/catalog reference including revision, when applicable. */
    val reference: String? = null,
    val notes: String? = null,
) {
    val isSynthetic: Boolean get() = status == DataStatus.SYNTHETIC_DEMO

    /** True only for data the owner has verified against a catalog or measurement. */
    val isEngineeringGrade: Boolean
        get() = status == DataStatus.CATALOG_VERIFIED || status == DataStatus.FIELD_MEASURED

    companion object {
        const val SYNTHETIC_SOURCE = "WellSketch synthetic demonstration fixture"

        fun syntheticDemo(notes: String? = null) = DataProvenance(
            status = DataStatus.SYNTHETIC_DEMO,
            source = SYNTHETIC_SOURCE,
            notes = notes ?: "Synthetic demonstration value. Not real well, catalog or test data.",
        )

        fun userEntered(notes: String? = null) = DataProvenance(
            status = DataStatus.USER_ENTERED,
            source = "User entry",
            notes = notes,
        )
    }
}

@Serializable
enum class DataStatus(val label: String) {
    /** Synthetic demonstration value; must never be used for real engineering decisions. */
    SYNTHETIC_DEMO("Synthetic demo"),

    /** Entered by the user; not yet verified against a catalog or measurement. */
    USER_ENTERED("User entered"),

    /** Nominal value from a cited catalog/standard, verified by the owner. */
    CATALOG_VERIFIED("Catalog verified"),

    /** Measured value (e.g. caliper, inspection report). */
    FIELD_MEASURED("Field measured"),

    /** Imported or legacy data whose source has not been verified. */
    UNVERIFIED("Unverified"),
}
