package com.michis.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryDecorationSearchPlanTest {
    @Test
    fun cachedTermsAreNotSearchedAgainRegardlessOfCase() {
        val plan = DictionaryDecorationSearchPlan.create(
            entries = listOf(10L to "Cessrt", 20L to "Nueva palabra"),
            cachedKeys = setOf("cessrt")
        )

        assertEquals(setOf("nueva palabra"), plan.missingCacheKeys)
    }

    @Test
    fun duplicateEffectiveTermsProduceOneSearchAndOneDecorationOwner() {
        val plan = DictionaryDecorationSearchPlan.create(
            entries = listOf(10L to "Árbol", 20L to "  ÁRBOL  "),
            cachedKeys = emptySet()
        )

        assertEquals(1, plan.activeTerms.size)
        assertEquals(10L, plan.activeTerms.single().entryIdentifier)
        assertEquals(setOf("árbol"), plan.missingCacheKeys)
    }

    @Test
    fun anEmptyCachedResultStillPreventsRepeatingTheSearch() {
        val plan = DictionaryDecorationSearchPlan.create(
            entries = listOf(4L to "Palabra inexistente"),
            cachedKeys = setOf("palabra inexistente")
        )

        assertTrue(plan.missingCacheKeys.isEmpty())
    }
}
