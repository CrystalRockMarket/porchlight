package it.kituwa.porchlight

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class StubRequest(
    val fullPath: String,
    val path: String,
    val method: String,
    val authorization: String?,
    val body: String,
)

/**
 * Minimal stand-in for a self-hosted backend. Used to pin down response parsing
 * against recorded real payloads without needing a live server in CI.
 */
class StubServer(
    private val handler: (StubRequest) -> Pair<Int, String>,
) : AutoCloseable {

    private val server = MockWebServer()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (status, payload) = handler(
                    StubRequest(
                        fullPath = request.path ?: "/",
                        path = (request.path ?: "/").substringBefore("?"),
                        method = request.method.orEmpty(),
                        authorization = request.getHeader("Authorization"),
                        body = request.body.readUtf8(),
                    ),
                )
                return MockResponse()
                    .setResponseCode(status)
                    .setHeader("Content-Type", "application/json")
                    .setBody(payload)
            }
        }
        server.start()
    }

    val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    override fun close() = server.shutdown()
}
