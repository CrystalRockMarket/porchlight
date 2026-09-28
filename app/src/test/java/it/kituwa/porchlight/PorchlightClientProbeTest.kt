package it.kituwa.porchlight

import it.kituwa.porchlight.data.AuthKind
import it.kituwa.porchlight.data.Reachability
import it.kituwa.porchlight.data.Server
import it.kituwa.porchlight.data.ServerSnapshot
import it.kituwa.porchlight.data.SecretState
import it.kituwa.porchlight.data.ServerType
import it.kituwa.porchlight.data.PorchlightClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * The Test connection button probes a server that has never been saved, so its
 * credentials exist only in memory. An earlier version read the secret back out of
 * the store and sent "Bearer " with an empty token, which made the button fail for
 * every user. These tests pin that down.
 */
class PorchlightClientProbeTest {

    private val saved = mutableListOf<ServerSnapshot>()

    private fun client(storeLookup: (String) -> SecretState = { SecretState.Absent }) = PorchlightClient(
        readSecret = { id -> storeLookup(id) },
        writeSnapshot = { snapshot -> saved += snapshot },
        readServers = { emptyList() },
    )

    private fun probeServer(url: String, type: ServerType = ServerType.GRAFANA) = Server(
        id = "probe",
        name = "probe",
        type = type,
        baseUrl = url,
        authKind = AuthKind.TOKEN,
        secret = "glsa_plaintext",
    )

    private fun grafanaStub(failWith: Exception? = null) = StubServer { request ->
        failWith?.let { throw it }
        when (request.path) {
            "/api/user" -> 200 to """{"id":1,"role":"Viewer"}"""
            "/api/health" -> 200 to """{"version":"13.2.2"}"""
            else -> 200 to "[]"
        }
    }

    @Test
    fun unsavedServerIsProbedWithTheSecretItWasGiven() = runBlocking {
        val authHeaders = mutableListOf<String?>()
        StubServer { request ->
            authHeaders += request.authorization
            when (request.path) {
                "/api/user" -> 200 to """{"id":1,"role":"Viewer"}"""
                "/api/health" -> 200 to """{"version":"13.2.2"}"""
                else -> 200 to "[]"
            }
        }.use { stub ->
            val snapshot = client().refresh(
                server = probeServer(stub.baseUrl),
                persist = false,
                secretOverride = "glsa_plaintext",
            )

            assertEquals(Reachability.REACHABLE, snapshot.reachability)
            assertTrue(
                "probe must send the supplied token, sent: $authHeaders",
                authHeaders.isNotEmpty() && authHeaders.all { it == "Bearer glsa_plaintext" },
            )
        }
    }

    @Test
    fun probeIsNotWrittenToTheSnapshotCache() = runBlocking {
        grafanaStub().use { stub ->
            client().refresh(probeServer(stub.baseUrl), persist = false, secretOverride = "t")
            assertTrue("a probe must not pollute the cache", saved.isEmpty())
        }
    }

    @Test
    fun savedServerReadsItsSecretBackFromTheStore() = runBlocking {
        val authHeaders = mutableListOf<String?>()
        StubServer { request ->
            authHeaders += request.authorization
            when (request.path) {
                "/api/user" -> 200 to """{"id":1}"""
                "/api/health" -> 200 to """{"version":"13.2.2"}"""
                else -> 200 to "[]"
            }
        }.use { stub ->
            val savedServer = probeServer(stub.baseUrl).copy(secret = "encrypted-blob")
            client { SecretState.Value("decrypted-from-keystore") }.refresh(savedServer, persist = true)

            assertTrue(authHeaders.all { it == "Bearer decrypted-from-keystore" })
            assertEquals(1, saved.size)
        }
    }

    private fun describe(error: Throwable) = client().describe(error)

    @Test
    fun timeoutsExplainThemselvesInsteadOfLeakingAJavaMessage() {
        assertEquals(
            "The server did not respond in time. Check the address and that it is still running.",
            describe(SocketTimeoutException("timeout")),
        )
    }

    @Test
    fun dnsFailuresExplainThemselves() {
        assertTrue(describe(UnknownHostException("no such host")).contains("could not be resolved"))
    }

    @Test
    fun tlsFailuresSuggestFallingBackToHttp() {
        assertTrue(describe(SSLException("cert not trusted")).contains("http://"))
    }

    @Test
    fun refusedConnectionsMentionThePort() {
        val message = describe(ConnectException("Failed to connect to /10.0.2.2:3300"))
        assertTrue(message.contains("port"))
        assertTrue("must not leak the raw OkHttp path", !message.contains("/"))
    }

    @Test
    fun unreachableServerIsNeverReportedAsHealthy() = runBlocking {
        val snapshot = client().refresh(
            probeServer("http://127.0.0.1:1"),
            persist = false,
            secretOverride = "t",
        )
        assertEquals(Reachability.UNREACHABLE, snapshot.reachability)
    }
}

/**
 * "Last successful check" used to display the time of the failed attempt, so a
 * server that had been down for days still claimed to have been checked
 * seconds ago. The timestamp must only ever move forward on a real success.
 */
class LastSuccessTimestampTest {

    private val good = ServerSnapshot("a", Reachability.REACHABLE, observedAt = 1_000L, lastSuccessAt = 1_000L)
    private val bad = ServerSnapshot("a", Reachability.UNREACHABLE, observedAt = 2_000L)

    private fun client(stored: ServerSnapshot?) = PorchlightClient(
        readSecret = { SecretState.Absent },
        writeSnapshot = {},
        readServers = { emptyList() },
        readSnapshot = { stored },
    )

    private fun server(url: String) = Server(
        id = "a",
        name = "a",
        type = ServerType.GRAFANA,
        baseUrl = url,
        authKind = AuthKind.TOKEN,
    )

    @Test
    fun successRecordsTheSuccessTime() = runBlocking {
        StubServer { request ->
            when (request.path) {
                "/api/user" -> 200 to """{"id":1}"""
                "/api/health" -> 200 to """{"version":"13.2.2"}"""
                else -> 200 to "[]"
            }
        }.use { stub ->
            val snapshot = client(null).refresh(server(stub.baseUrl), persist = false, secretOverride = "t")
            assertEquals(snapshot.observedAt, snapshot.lastSuccessAt)
        }
    }

    @Test
    fun failureCarriesForwardThePreviousSuccessTime() = runBlocking {
        val snapshot = client(good).refresh(
            server("http://127.0.0.1:1"),
            persist = false,
            secretOverride = "t",
        )
        assertEquals(Reachability.UNREACHABLE, snapshot.reachability)
        assertEquals(1_000L, snapshot.lastSuccessAt)
        assertTrue("the failure time is newer", snapshot.observedAt > 1_000L)
    }

    @Test
    fun failureWithNoHistoryHasNoSuccessTime() = runBlocking {
        val snapshot = client(null).refresh(
            server("http://127.0.0.1:1"),
            persist = false,
            secretOverride = "t",
        )
        assertEquals(null, snapshot.lastSuccessAt)
    }

    @Test
    fun failureDoesNotOverwriteAGoodStoredSuccess() = runBlocking {
        val snapshot = client(good).refresh(
            server("http://127.0.0.1:1"),
            persist = false,
            secretOverride = "t",
        )
        assertTrue(snapshot.lastSuccessAt!! < snapshot.observedAt)
    }
}
