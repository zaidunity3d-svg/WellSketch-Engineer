package com.wellsketch.engineer.core.data.demo

import com.wellsketch.engineer.core.model.equipment.EquipmentCategory
import com.wellsketch.engineer.core.model.equipment.EquipmentDimensions
import com.wellsketch.engineer.core.model.equipment.EquipmentItem
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.units.Length
import com.wellsketch.engineer.core.model.units.Pressure

/**
 * SYNTHETIC equipment library entries used by the demo projects.
 *
 * These are placeholders that exercise the library UI and provenance handling. They are
 * not catalog data: no manufacturer, part number, API table value or rating is claimed.
 * Real entries must be imported from validated datasets with catalog provenance.
 */
object SyntheticEquipmentCatalog {
    const val CONDUCTOR_20 = "demo-eq-conductor-20"
    const val CASING_13_375 = "demo-eq-casing-13375"
    const val CASING_9_625 = "demo-eq-casing-9625"
    const val CASING_7 = "demo-eq-casing-7"
    const val LINER_4_5 = "demo-eq-liner-45"
    const val TUBING_3_5 = "demo-eq-tubing-35"
    const val CT_STRING_175 = "demo-eq-ct-175"

    private val synthetic = DataProvenance.syntheticDemo(
        "Synthetic placeholder. Replace with a validated catalog entry before engineering use.",
    )

    private fun tubular(id: String, category: EquipmentCategory, name: String, odIn: Double, idIn: Double) =
        EquipmentItem(
            id = id,
            category = category,
            name = name,
            dimensions = EquipmentDimensions(outerDiameter = Length.inches(odIn), innerDiameter = Length.inches(idIn)),
            provenance = synthetic,
        )

    val items: List<EquipmentItem> = listOf(
        tubular(CONDUCTOR_20, EquipmentCategory.CASING, "20 in conductor (synthetic demo)", 20.0, 19.0),
        tubular(CASING_13_375, EquipmentCategory.CASING, "13-3/8 in casing (synthetic demo)", 13.375, 12.4),
        tubular(CASING_9_625, EquipmentCategory.CASING, "9-5/8 in casing (synthetic demo)", 9.625, 8.7),
        tubular(CASING_7, EquipmentCategory.CASING, "7 in casing (synthetic demo)", 7.0, 6.2),
        tubular(LINER_4_5, EquipmentCategory.LINER, "4-1/2 in liner (synthetic demo)", 4.5, 3.9),
        tubular(TUBING_3_5, EquipmentCategory.TUBING, "3-1/2 in tubing (synthetic demo)", 3.5, 2.9),
        EquipmentItem(
            id = CT_STRING_175,
            category = EquipmentCategory.CT_STRING,
            name = "1.75 in coiled tubing (synthetic demo)",
            dimensions = EquipmentDimensions(
                outerDiameter = Length.inches(1.75),
                wallThickness = Length.inches(0.134),
            ),
            provenance = synthetic,
        ),
        EquipmentItem(
            id = "demo-eq-packer",
            category = EquipmentCategory.PACKER,
            name = "Production packer (synthetic demo)",
            dimensions = EquipmentDimensions(outerDiameter = Length.inches(5.9), innerDiameter = Length.inches(2.9)),
            provenance = synthetic,
        ),
        EquipmentItem(
            id = "demo-eq-bridge-plug",
            category = EquipmentCategory.PLUG,
            name = "Bridge plug (synthetic demo)",
            dimensions = EquipmentDimensions(outerDiameter = Length.inches(5.9)),
            provenance = synthetic,
        ),
        EquipmentItem(
            id = "demo-eq-esp",
            category = EquipmentCategory.ESP,
            name = "ESP assembly (synthetic demo)",
            provenance = synthetic,
        ),
        EquipmentItem(
            id = "demo-eq-ct-bop",
            category = EquipmentCategory.CT_PRESSURE_CONTROL,
            name = "CT BOP (synthetic demo)",
            dimensions = EquipmentDimensions(bore = Length.inches(3.0), ratedWorkingPressure = Pressure.psi(5000.0)),
            provenance = synthetic,
        ),
        EquipmentItem(
            id = "demo-eq-stripper",
            category = EquipmentCategory.CT_PRESSURE_CONTROL,
            name = "Stripper / packoff (synthetic demo)",
            dimensions = EquipmentDimensions(bore = Length.inches(3.0), ratedWorkingPressure = Pressure.psi(5000.0)),
            provenance = synthetic,
        ),
        EquipmentItem(
            id = "demo-eq-master-valve",
            category = EquipmentCategory.CHRISTMAS_TREE,
            name = "Master valve (synthetic demo)",
            dimensions = EquipmentDimensions(bore = Length.inches(3.0), ratedWorkingPressure = Pressure.psi(5000.0)),
            provenance = synthetic,
        ),
        EquipmentItem(
            id = "demo-eq-ct-connector",
            category = EquipmentCategory.CT_BHA,
            name = "CT connector (synthetic demo)",
            dimensions = EquipmentDimensions(outerDiameter = Length.inches(2.125), length = Length.meters(0.3)),
            provenance = synthetic,
        ),
        EquipmentItem(
            id = "demo-eq-injector",
            category = EquipmentCategory.CT_SURFACE,
            name = "Injector head (synthetic demo)",
            provenance = synthetic,
        ),
    )
}
