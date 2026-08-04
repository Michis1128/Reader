package com.michis.reader.sync.drive

import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Transporte HTTP compartido para Drive, con errores y tiempos de espera consistentes. */
internal class GoogleDriveHttpClient {
    fun json(
        url: String,
        accessToken: String,
        method: String = "GET",
        body: String? = null
    ): JSONObject = JSONObject(
        bytes(
            url = url,
            accessToken = accessToken,
            method = method,
            contentType = body?.let { JSON_CONTENT_TYPE },
            body = body?.toByteArray(Charsets.UTF_8)
        ).toString(Charsets.UTF_8)
    )

    fun bytes(
        url: String,
        accessToken: String,
        method: String = "GET",
        contentType: String? = null,
        body: ByteArray? = null
    ): ByteArray = execute(url, accessToken, method, contentType, body) { connection ->
        connection.inputStream.use { it.readBytes() }
    }

    fun download(url: String, accessToken: String, output: OutputStream) {
        execute(url, accessToken, "GET", null, null, READ_TIMEOUT_DOWNLOAD_MILLISECONDS) { connection ->
            connection.inputStream.buffered().use { it.copyTo(output) }
        }
    }

    private fun <T> execute(
        url: String,
        accessToken: String,
        method: String,
        contentType: String?,
        body: ByteArray?,
        readTimeoutMilliseconds: Int = READ_TIMEOUT_MILLISECONDS,
        readSuccess: (HttpURLConnection) -> T
    ): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MILLISECONDS
            connection.readTimeout = readTimeoutMilliseconds
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                connection.outputStream.use { it.write(body) }
            }
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) throw driveError(connection, statusCode)
            return readSuccess(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun driveError(connection: HttpURLConnection, statusCode: Int): IllegalStateException {
        val responseText = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val apiMessage = runCatching {
            JSONObject(responseText).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()
        return IllegalStateException(
            "Drive respondió $statusCode${if (apiMessage.isBlank()) "" else ": $apiMessage"}"
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLISECONDS = 15_000
        const val READ_TIMEOUT_MILLISECONDS = 30_000
        const val READ_TIMEOUT_DOWNLOAD_MILLISECONDS = 120_000
        const val JSON_CONTENT_TYPE = "application/json; charset=UTF-8"
    }
}
