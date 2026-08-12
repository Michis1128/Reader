package com.michis.reader.sync

import org.json.JSONArray
import org.json.JSONObject

/** Reglas puras que impiden que una sincronización direccional sobrescriba cambios más recientes. */
internal object DirectionalSyncPolicy {
    fun shouldUpload(localUpdatedAt: Long, remoteUpdatedAt: Long?): Boolean =
        remoteUpdatedAt == null || localUpdatedAt > remoteUpdatedAt

    fun mergeTombstones(remote: JSONArray, local: JSONArray): JSONArray {
        val latestByKey = linkedMapOf<String, JSONObject>()
        fun include(source: JSONArray) {
            repeat(source.length()) { index ->
                val item = source.optJSONObject(index) ?: return@repeat
                val key = "${item.optString("entityType")}:${item.optString("syncId")}"
                val previous = latestByKey[key]
                if (previous == null || item.optLong("deletedAt") > previous.optLong("deletedAt")) {
                    latestByKey[key] = JSONObject(item.toString())
                }
            }
        }
        include(remote)
        include(local)
        return JSONArray().apply { latestByKey.values.forEach(::put) }
    }
}
