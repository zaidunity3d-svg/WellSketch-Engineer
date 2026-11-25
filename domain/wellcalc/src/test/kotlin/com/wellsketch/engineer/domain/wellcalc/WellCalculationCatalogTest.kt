package com.wellsketch.engineer.domain.wellcalc

import kotlin.test.Test
import kotlin.test.assertTrue

class WellCalculationCatalogTest {
    @Test
    fun noCalculationIsOfferedBeforeItsSpecificationIsReviewed() {
        val specs = WellCalculationCatalog.specifications
        assertTrue(specs.isNotEmpty())
        assertTrue(specs.all { it.status == CalculationStatus.AWAITING_SPECIFICATION_REVIEW && it.version == null })
        assertTrue(specs.map { it.id }.toSet().size == specs.size, "ids must be unique")
    }
}
