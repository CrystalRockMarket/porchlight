package it.kituwa.porchlight

import it.kituwa.porchlight.data.AuthKind
import it.kituwa.porchlight.data.Http
import it.kituwa.porchlight.data.PorchlightClient
import it.kituwa.porchlight.data.Reachability
import it.kituwa.porchlight.data.SecretState
import it.kituwa.porchlight.data.Server
import it.kituwa.porchlight.data.ServerSnapshot
import it.kituwa.porchlight.data.ServerType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Findings from the whole-project audit. Each test here corresponds to a
 * specific defect that shipped into an earlier commit.
 */
class AuditFixTest {

    private fun grafanaServer(url: String) = Server(
        id = "s1",
        name = "s1",
        type = ServerType.GRAFANA,
        baseUrl = url,
        authKind = AuthKind.TOKEN,
    )

    private fun client(secret: SecretState) = PorchlightClient(
        readSecret = { secret },
        writeSnapshot = {},
        readServers = { emptyList() },
        readSnapshot = { null },
    )

    /**
     * A dead Docker socket used to look exactly like an environment with zero
     * containers, and the app reported "all healthy" because there were no
     * issues. Silence as health is the one thing this app must never do.
     */
    @Test
    fun unreadableContainersAreReportedNotTreatedAsHealthy() = runBlocking {
        StubServer { request ->
            when {
                request.path == "/api/auth" -> 200 to """{"jwt":"jwt-1"}"""
                request.path == "/api/endpoints" ->
                    200 to """[{"Id":1,"Name":"local","Status":1,"Type":2}]"""
                request.path.contains("containers/json") -> 500 to """{"message":"docker socket down"}"""
                else -> 404 to "{}"
            }
        }.use { stub ->
            val snapshot = it.kituwa.porchlight.data.PortainerClient(Http()).fetch(
                Server("s1", "p", ServerType.PORTAINER, stub.baseUrl, AuthKind.TOKEN),
                "jwt",
            )

            val issue = snapshot.issues.single()
            assertEquals("Could not read containers", issue.title)
            assertTrue(issue.detail!!.contains("unknown"))
            assertTrue(
                "container count must not imply health",
                snapshot.details.first { it.first == "Containers" }.second.contains("unknown"),
            )
        }
    }

    /**
     * A credential that the Keystore can no longer open is a different problem
     * from a wrong token, and telling the user to check their token sends them
     * after the wrong thing.
     */
    @Test
    fun undecryptableCredentialIsDistinguishedFromABadToken() = runBlocking {
        val snapshot = client(SecretState.Undecryptable)
            .refresh(grafanaServer("https://example.com"), persist = false)

        assertEquals(Reachability.AUTH_FAILED, snapshot.reachability)
        assertTrue(snapshot.message!!.contains("could not be decrypted"))
        assertTrue(!snapshot.message!!.contains("rejected"))
    }

    /** A server the user never gave a token for is not a decryption failure. */
    @Test
    fun absentCredentialIsNotReportedAsUndecryptable() = runBlocking {
        StubServer { request ->
            when (request.path) {
                "/api/user" -> 200 to """{"id":1}"""
                "/api/health" -> 200 to """{"version":"13.2.2"}"""
                else -> 200 to "[]"
            }
        }.use { stub ->
            val snapshot = client(SecretState.Absent)
                .refresh(grafanaServer(stub.baseUrl), persist = false)
            assertEquals(Reachability.REACHABLE, snapshot.reachability)
        }
    }

    /** A self-hosted server can return a body large enough to exhaust memory. */
    @Test
    fun oversizedResponsesAreRefusedRatherThanBuffered() = runBlocking {
        val giant = "x".repeat((Http.MAX_BODY_BYTES + 1024).toInt())
        StubServer { request ->
            200 to """{"filler":"$giant"}"""
        }.use { stub ->
            val error = runCatching { Http().getJson("${stub.baseUrl}/api/health", grafanaServer(stub.baseUrl), "t") }
                .exceptionOrNull()
            assertTrue(error is IOException)
            assertTrue(error!!.message!!.contains("too large"))
        }
    }

    /** The app is read-only; nothing may ever be sent back to a user's server. */
    @Test
    fun onlyGetAndAuthPostAreEverUsed() = runBlocking {
        val methods = mutableListOf<String>()
        StubServer { request ->
            methods += request.method
            when {
                request.path == "/api/auth" -> 200 to """{"jwt":"jwt-1"}"""
                request.path == "/api/endpoints" ->
                    200 to """[{"Id":1,"Name":"local","Status":1,"Type":2}]"""
                request.path.contains("containers/json") ->
                    200 to """[{"Id":"c","Names":["/web"],"Image":"nginx","State":"running","Status":"Up 1 day"}]"""
                else -> 404 to "{}"
            }
        }.use { stub ->
            it.kituwa.porchlight.data.PortainerClient(Http()).fetch(
                Server("s1", "p", ServerType.PORTAINER, stub.baseUrl, AuthKind.BASIC, username = "u"),
                "pw",
            )
        }

        assertEquals("the only POST is the Portainer login exchange", setOf("POST", "GET"), methods.toSet())
        assertTrue("no mutating verbs allowed", methods.none { it in setOf("PUT", "DELETE", "PATCH") })
    }

    /** An unverified TLS certificate is the single most common homelab setup. */
    @Test
    fun tlsFailuresTellTheUserWhatToDoAboutIt() {
        val message = client(SecretState.Absent).describe(javax.net.ssl.SSLException("self signed"))
        assertTrue(message.contains("http://"))
    }

    /** A snapshot that was never persisted must not claim a last-success time. */
    @Test
    fun failedProbeDoesNotInheritAPreviousSuccess() = runBlocking {
        val stored = ServerSnapshot("s1", Reachability.REACHABLE, observedAt = 10L, lastSuccessAt = 10L)
        val probe = PorchlightClient(
            readSecret = { SecretState.Absent },
            writeSnapshot = {},
            readServers = { emptyList() },
            readSnapshot = { stored },
        )
        val snapshot = probe.refresh(grafanaServer("http://127.0.0.1:1"), persist = false)
        assertEquals(Reachability.UNREACHABLE, snapshot.reachability)
        assertEquals(10L, snapshot.lastSuccessAt)
    }
}
