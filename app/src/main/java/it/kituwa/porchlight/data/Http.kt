package it.kituwa.porchlight.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
            val payload = readBounded(response)
            if (!response.isSuccessful) throw HttpException(response.code, payload)
            if (payload.isBlank()) return json.parseToJsonElement("{}")
            return runCatching { json.parseToJsonElement(payload) }
                .getOrElse { throw IOException("Unexpected response format from ${server.name}") }
        }
    }

    /**
     * A self-hosted server that is misbehaving can return an arbitrarily large
     * body. Reading it whole would risk an out-of-memory crash, and a status page
     * on a dead host is a plausible way to trigger it.
     */
    private fun readBounded(response: Response): String {
        val body = response.body ?: return ""
        val declared = body.contentLength()
        if (declared > MAX_BODY_BYTES) throw IOException("Response too large to read safely")
        val source = body.source()
        source.request(MAX_BODY_BYTES + 1)
        val buffered = source.buffer.size
        if (buffered > MAX_BODY_BYTES) throw IOException("Response too large to read safely")
        return source.readUtf8(buffered)
    }

    companion object {
        const val MAX_BODY_BYTES = 8L * 1024 * 1024
        val mediaType = "application/json; charset=utf-8".toMediaType()
    }
}
