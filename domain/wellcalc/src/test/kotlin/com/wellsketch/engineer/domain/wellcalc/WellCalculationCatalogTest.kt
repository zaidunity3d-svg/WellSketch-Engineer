package com.wellsketch.engineer.domain.wellcalc

import kotlin.test.Test
import kotlin.test.assertTrue

class WellCalculationCatalogTest {
    @Test
    fun implementedCalculationsAreVersionedReferencedAndNotYetMarkedValidated() {
        val specs = WellCalculationCatalog.specifications
        assertTrue(specs.isNotEmpty())
        assertTrue(specs.map { it.id }.toSet().size == specs.size, "ids must be unique")
        for (s in specs) {
            // Only the owner can mark a calculation VALIDATED after reviewing it.
            assertTrue(s.status != CalculationStatus.VALIDATED, s.id)
            if (s.status == CalculationStatus.IMPLEMENTED_PENDING_VALIDATION) {
                assertTrue(s.version != null && s.references.isNotEmpty(), "${s.id} needs a version and its formula basis")
            }
        }
    }
}
