package com.wellsketch.engineer.domain.equipment

import com.wellsketch.engineer.core.data.demo.SyntheticEquipmentCatalog
import com.wellsketch.engineer.core.model.equipment.EquipmentCategory
import com.wellsketch.engineer.core.model.equipment.EquipmentItem
import com.wellsketch.engineer.core.model.equipment.ReferenceImage
import com.wellsketch.engineer.core.model.provenance.DataProvenance
import com.wellsketch.engineer.core.model.provenance.DataStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EquipmentLibraryTest {
    private val library = EquipmentLibrary()

    @Test
    fun syntheticEntriesAreNeverEngineeringGrade() {
        SyntheticEquipmentCatalog.items.forEach { assertFalse(library.readiness(it).isEngineeringGrade, it.id) }
    }

    @Test
    fun catalogVerifiedEntryNeedsReferenceAndApprovedImageForBreakdown() {
        val item = EquipmentItem(
            id = "x", category = EquipmentCategory.PACKER, name = "Packer",
            provenance = DataProvenance(DataStatus.CATALOG_VERIFIED, "Catalog", reference = null),
        )
        assertFalse(library.readiness(item).isEngineeringGrade)
        val withRef = item.copy(
            provenance = item.provenance.copy(reference = "Catalog rev. A"),
            referenceImages = listOf(ReferenceImage("i", "Breakdown", "assets/x.png", approvedByOwner = true)),
        )
        val r = library.readiness(withRef)
        assertTrue(r.isEngineeringGrade && r.hasApprovedBreakdownImage)
    }

    @Test
    fun groupingFollowsCategoryOrderAndFilters() {
        val groups = library.group(SyntheticEquipmentCatalog.items)
        assertEquals(groups.map { it.category }.sortedBy { it.ordinal }, groups.map { it.category })
        val filtered = library.group(SyntheticEquipmentCatalog.items, "packer")
        assertEquals(listOf(EquipmentCategory.PACKER), filtered.map { it.category })
    }
}
