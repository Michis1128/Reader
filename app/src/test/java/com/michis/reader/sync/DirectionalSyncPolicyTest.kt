package com.michis.reader.sync

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DirectionalSyncPolicyTest {
    @Test
    fun uploadOnlySendsNewOrNewerLocalState() {
        assertTrue(DirectionalSyncPolicy.shouldUpload(localUpdatedAt = 20, remoteUpdatedAt = null))
        assertTrue(DirectionalSyncPolicy.shouldUpload(localUpdatedAt = 20, remoteUpdatedAt = 10))
        assertFalse(DirectionalSyncPolicy.shouldUpload(localUpdatedAt = 20, remoteUpdatedAt = 20))
        assertFalse(DirectionalSyncPolicy.shouldUpload(localUpdatedAt = 20, remoteUpdatedAt = 30))
    }

    @Test
    fun uploadManifestPreservesRemoteTombstonesAndKeepsNewestDeletion() {
        val remote = JSONArray()
            .put(tombstone("annotation", "shared", 30))
            .put(tombstone("dictionary_entry", "remote-only", 15))
        val local = JSONArray()
            .put(tombstone("annotation", "shared", 40))
            .put(tombstone("annotation", "local-only", 25))

        val merged = DirectionalSyncPolicy.mergeTombstones(remote, local)
        val byIdentifier = (0 until merged.length()).associate {
            val item = merged.getJSONObject(it)
            item.getString("syncId") to item.getLong("deletedAt")
        }

        assertEquals(3, merged.length())
        assertEquals(40L, byIdentifier["shared"])
        assertEquals(15L, byIdentifier["remote-only"])
        assertEquals(25L, byIdentifier["local-only"])
    }

    @Test
    fun localQuoteIsUploadedEvenWhenRemoteReadingStateIsNewer() {
        val remote = book(updatedAt = 500).put("annotations", JSONArray())
        val localQuote = versioned("local-quote", 200).put("kind", "cita")
        val local = book(updatedAt = 100).put("annotations", JSONArray().put(localQuote))

        val result = DirectionalSyncPolicy.mergeBookState(remote, local)

        assertTrue(result.containsLocalChanges)
        assertEquals("local-quote", result.value.getJSONArray("annotations").getJSONObject(0).getString("syncId"))
        assertEquals(500L, result.value.getLong("updatedAt"))
    }

    @Test
    fun quotesCreatedOnDifferentDevicesAreBothPreserved() {
        val remote = book(400).put("annotations", JSONArray().put(versioned("remote-quote", 300)))
        val local = book(200).put("annotations", JSONArray().put(versioned("local-quote", 150)))

        val annotations = DirectionalSyncPolicy.mergeBookState(remote, local).value.getJSONArray("annotations")
        val identifiers = (0 until annotations.length()).map {
            annotations.getJSONObject(it).getString("syncId")
        }.toSet()

        assertEquals(setOf("remote-quote", "local-quote"), identifiers)
    }

    private fun book(updatedAt: Long) = JSONObject()
        .put("syncId", "book")
        .put("documentKey", "fingerprint")
        .put("updatedAt", updatedAt)
        .put("annotations", JSONArray())
        .put("dictionaryCategories", JSONArray())
        .put("dictionaryLinks", JSONArray())
        .put("linkedDictionaryDocumentKeys", JSONArray())

    private fun versioned(identifier: String, updatedAt: Long) = JSONObject()
        .put("syncId", identifier)
        .put("updatedAt", updatedAt)

    private fun tombstone(type: String, identifier: String, deletedAt: Long) = JSONObject()
        .put("entityType", type)
        .put("syncId", identifier)
        .put("deletedAt", deletedAt)
}
