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

    private fun tombstone(type: String, identifier: String, deletedAt: Long) = JSONObject()
        .put("entityType", type)
        .put("syncId", identifier)
        .put("deletedAt", deletedAt)
}
