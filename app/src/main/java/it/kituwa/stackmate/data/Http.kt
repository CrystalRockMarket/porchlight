package it.kituwa.stackmate.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val status: Int, val body: String) : IOException("HTTP $status")

class Http {

    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val url = withScheme.toHttpUrlOrNull() ?: throw IOException("Not a valid URL: $raw")
        return url.toString().trimEnd('/')
    }

    suspend fun getJson(url: String, server: Server, secret: String, token: String? = null): JsonElement =
        withContext(Dispatchers.IO) { execute(url, "GET", null, server, secret, token) }

    suspend fun postJson(url: String, body: JsonObject, server: Server, secret: String): JsonElement =
        withContext(Dispatchers.IO) { execute(url, "POST", body, server, secret, null) }

    private fun execute(
        url: String,
        method: String,
        body: JsonObject?,
        server: Server,
        secret: String,
        token: String?,
    ): JsonElement {
        val builder = Request.Builder().url(url)

        when {
            token != null -> builder.header("Authorization", "Bearer $token")
            server.authKind == AuthKind.TOKEN -> builder.header("Authorization", "Bearer $secret")
            server.authKind == AuthKind.BASIC -> builder.header(
                "Authorization",
                okhttp3.Credentials.basic(server.username.orEmpty(), secret),
            )
            else -> Unit
        }

        if (body != null) {
            builder.method(
                method,
                json.encodeToString(JsonObject.serializer(), body).toRequestBody(mediaType),
            )
        } else {
            builder.method(method, null)
        }

        client.newCall(builder.build()).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpException(response.code, payload)
            if (payload.isBlank()) return json.parseToJsonElement("{}")
            return runCatching { json.parseToJsonElement(payload) }
                .getOrElse { throw IOException("Unexpected response format from ${server.name}") }
        }
    }

    companion object {
        val mediaType = "application/json; charset=utf-8".toMediaType()
    }
}
