package com.nahuel.homeflow.devices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Fires a user-defined HTTP endpoint (webhook). Works for Shelly, Tasmota, Home Assistant, IFTTT, etc. */
object GenericClient {
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun fire(url: String, method: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.startsWith("http")) { "URL muss mit http beginnen" }
            val builder = Request.Builder().url(url)
            if (method.equals("POST", ignoreCase = true)) {
                builder.post(body.toRequestBody(json))
            } else {
                builder.get()
            }
            // Generic devices may be local (self-signed) or internet; use the lenient local client.
            Http.local.newCall(builder.build()).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
            }
        }
    }
}
