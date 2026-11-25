package com.wellsketch.engineer.domain.equipment

import com.wellsketch.engineer.core.model.equipment.EquipmentCategory
import com.wellsketch.engineer.core.model.equipment.EquipmentItem
import com.wellsketch.engineer.core.model.provenance.DataStatus

data class EquipmentGroup(val category: EquipmentCategory, val items: List<EquipmentItem>)

/** Whether an equipment entry may feed engineering checks, with the reasons if not. */
data class EquipmentReadiness(
    val isEngineeringGrade: Boolean,
    val hasApprovedBreakdownImage: Boolean,
    val notes: List<String>,
)

/** Query and provenance rules for the equipment reference library. */
class EquipmentLibrary {

    fun group(items: List<EquipmentItem>, query: String = ""): List<EquipmentGroup> {
        val q = query.trim().lowercase()
        return items
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.category.label.lowercase().contains(q) }
            .groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })
            .map { (category, list) -> EquipmentGroup(category, list.sortedBy { it.name }) }
    }

    fun readiness(item: EquipmentItem): EquipmentReadiness {
        val notes = mutableListOf<String>()
        val p = item.provenance
        when (p.status) {
            DataStatus.SYNTHETIC_DEMO -> notes += "Synthetic demonstration entry – not catalog data."
            DataStatus.USER_ENTERED -> notes += "User-entered; verify against the manufacturer data sheet."
            DataStatus.UNVERIFIED -> notes += "Source not verified."
            DataStatus.CATALOG_VERIFIED -> if (p.reference.isNullOrBlank()) notes += "Catalog reference/revision missing."
            DataStatus.FIELD_MEASURED -> Unit
        }
        val approvedImage = item.referenceImages.any { it.approvedByOwner }
        if (!approvedImage) notes += "No owner-approved reference image: component breakdown unavailable."
        val engineeringGrade = p.isEngineeringGrade &&
            !(p.status == DataStatus.CATALOG_VERIFIED && p.reference.isNullOrBlank())
        return EquipmentReadiness(engineeringGrade, approvedImage, notes)
    }
}
