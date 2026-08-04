package com.michis.reader.reader

import java.util.Locale

internal data class DictionarySearchTerm(val entryIdentifier: Long, val term: String) {
    val cacheKey: String = normalizeDictionaryTerm(term)
}

internal data class DictionaryDecorationSearchPlan(
    val activeTerms: List<DictionarySearchTerm>,
    val missingCacheKeys: Set<String>
) {
    companion object {
        fun create(entries: Collection<Pair<Long, String>>, cachedKeys: Set<String>): DictionaryDecorationSearchPlan {
            val activeTerms = entries
                .asSequence()
                .map { DictionarySearchTerm(it.first, it.second.trim()) }
                .filter { it.cacheKey.isNotEmpty() }
                .distinctBy(DictionarySearchTerm::cacheKey)
                .toList()
            return DictionaryDecorationSearchPlan(
                activeTerms = activeTerms,
                missingCacheKeys = activeTerms.map(DictionarySearchTerm::cacheKey).filterNot(cachedKeys::contains).toSet()
            )
        }
    }
}

internal fun normalizeDictionaryTerm(term: String): String = term.trim().lowercase(Locale.ROOT)
