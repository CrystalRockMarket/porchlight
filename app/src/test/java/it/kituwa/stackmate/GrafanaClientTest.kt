package it.kituwa.stackmate

import it.kituwa.stackmate.data.AuthKind
import it.kituwa.stackmate.data.GrafanaClient
import it.kituwa.stackmate.data.Http
import it.kituwa.stackmate.data.Reachability
import it.kituwa.stackmate.data.Severity
import it.kituwa.stackmate.data.Server
import it.kituwa.stackmate.data.ServerType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrafanaClientTest {

    private fun server(baseUrl: String, auth: AuthKind = AuthKind.TOKEN) = Server(
        id = "1",
        name = "grafana",
        type = ServerType.GRAFANA,
        baseUrl = baseUrl,
        authKind = auth,
        secret = "glsa_test",
    )

    @Test
    fun readsVersionAndIgnoresResolvedAlerts() = runBlocking {
        StubServer { request ->
            when (request.path) {
                "/api/user" -> 200 to """{"id":1,"login":"sa-1-stackmate","role":"Viewer"}"""
                "/api/health" -> 200 to """{"database":"ok","version":"13.2.2","commit":"abc"}"""
                "/api/alertmanager/grafana/api/v2/alerts" -> 200 to """[
                    {"labels":{"alertname":"Resolved thing","severity":"critical"},
                     "annotations":{"summary":"was a problem"},
                     "status":"OK","startsAt":"2026-09-01T10:00:00Z"},
                    {"labels":{},"status":"OK"}
                ]"""
                else -> 404 to "{}"
            }
        }.use { stub ->
            val snapshot = GrafanaClient(Http()).fetch(server(stub.baseUrl), "glsa_test")

            assertEquals(Reachability.REACHABLE, snapshot.reachability)
            assertEquals("13.2.2", snapshot.details.first { it.first == "Grafana" }.second)
            assertEquals("2", snapshot.details.first { it.first == "Firing" }.second)
            assertTrue("resolved alerts must not be reported", snapshot.issues.isEmpty())
        }
    }

    @Test
    fun mapsFiringAlertSeverityAndTimestamp() = runBlocking {
        StubServer { request ->
            when (request.path) {
                "/api/user" -> 200 to """{"id":1,"login":"sa-1-stackmate","role":"Viewer"}"""
                "/api/health" -> 200 to """{"version":"13.2.2"}"""
                "/api/alertmanager/grafana/api/v2/alerts" -> 200 to """[
                    {"labels":{"alertname":"DatabaseDown","severity":"critical"},
                     "annotations":{"summary":"postgres unreachable"},
                     "status":"firing","startsAt":"2026-09-20T08:30:00Z"},
                    {"labels":{"alertname":"DiskFilling","severity":"warning"},
                     "annotations":{"summary":"disk at 95%"},
                     "status":"firing","startsAt":"2026-09-20T09:15:00Z"}
                ]"""
                else -> 404 to "{}"
            }
        }.use { stub ->
            val snapshot = GrafanaClient(Http()).fetch(server(stub.baseUrl), "glsa_test")

            assertEquals(2, snapshot.issues.size)
            assertEquals("DatabaseDown", snapshot.issues[0].title)
            assertEquals(Severity.CRITICAL, snapshot.issues[0].severity)
            assertEquals("postgres unreachable", snapshot.issues[0].detail)
            assertNotNull(snapshot.issues[0].since)
            assertEquals(Severity.WARNING, snapshot.issues[1].severity)
        }
    }

    @Test
    fun unknownSeverityIsTreatedAsInfoNotCritical() = runBlocking {
        StubServer { request ->
            when (request.path) {
                "/api/user" -> 200 to """{"id":1,"login":"sa-1-stackmate","role":"Viewer"}"""
                "/api/health" -> 200 to """{"version":"13.2.2"}"""
                "/api/alertmanager/grafana/api/v2/alerts" -> 200 to """[
                    {"labels":{"alertname":"Odd","severity":"weird"},"status":"firing"}
                ]"""
                else -> 404 to "{}"
            }
        }.use { stub ->
            val snapshot = GrafanaClient(Http()).fetch(server(stub.baseUrl), "glsa_test")
            assertEquals(Severity.INFO, snapshot.issues.single().severity)
        }
    }

    @Test
    fun forbiddenAlertApiIsReportedNotSilentlyZero() = runBlocking {
        StubServer { request ->
            when (request.path) {
                "/api/user" -> 200 to """{"id":1,"login":"sa-1-stackmate","role":"Viewer"}"""
                "/api/health" -> 200 to """{"version":"13.2.2"}"""
                "/api/alertmanager/grafana/api/v2/alerts" -> 403 to """{"message":"Forbidden"}"""
                else -> 404 to "{}"
            }
        }.use { stub ->
            val snapshot = GrafanaClient(Http()).fetch(server(stub.baseUrl), "glsa_test")

            assertEquals(Reachability.REACHABLE, snapshot.reachability)
            assertTrue(
                "a forbidden alerts API must not look like zero alerts",
                snapshot.details.any { it.first == "Firing" && it.second == "unavailable" },
            )
            assertTrue(snapshot.details.any { it.first == "Alert API" })
        }
    }

    @Test
    fun publicHealthEndpointDoesNotMaskABadToken() = runBlocking {
        StubServer { request ->
            when (request.path) {
                "/api/user" -> 401 to """{"message":"Unauthorized"}"""
                "/api/health" -> 200 to """{"database":"ok","version":"13.2.2"}"""
                else -> 404 to "{}"
            }
        }.use { stub ->
            val error = runCatching { GrafanaClient(Http()).fetch(server(stub.baseUrl), "bad") }
                .exceptionOrNull()
            assertTrue(error is it.kituwa.stackmate.data.ReachabilityException)
            assertEquals(
                Reachability.AUTH_FAILED,
                (error as it.kituwa.stackmate.data.ReachabilityException).reachability,
            )
        }
    }

    @Test
    fun rejectedTokenSurfacesAsAuthFailure() = runBlocking {
        StubServer { request ->
            if (request.path == "/api/user") 401 to """{"message":"Unauthorized"}""" else 404 to "{}"
        }.use { stub ->
            val error = runCatching { GrafanaClient(Http()).fetch(server(stub.baseUrl), "bad") }
                .exceptionOrNull()
            assertTrue(error is it.kituwa.stackmate.data.ReachabilityException)
            assertEquals(
                Reachability.AUTH_FAILED,
                (error as it.kituwa.stackmate.data.ReachabilityException).reachability,
            )
        }
    }

    @Test
    fun sendsBearerTokenForTokenAuth() = runBlocking {
        val authHeaders = mutableListOf<String?>()
        StubServer { request ->
            if (request.path == "/api/user") authHeaders += request.authorization
            when (request.path) {
                "/api/user" -> 200 to """{"id":1}"""
                "/api/health" -> 200 to """{"version":"13.2.2"}"""
                else -> 200 to "[]"
            }
        }.use { stub ->
            GrafanaClient(Http()).fetch(server(stub.baseUrl, AuthKind.TOKEN), "glsa_secret")
        }
        assertEquals(listOf("Bearer glsa_secret"), authHeaders)
    }

    @Test
    fun sendsBasicCredentialsForBasicAuth() = runBlocking {
        val authHeaders = mutableListOf<String?>()
        StubServer { request ->
            if (request.path == "/api/user") authHeaders += request.authorization
            when (request.path) {
                "/api/user" -> 200 to """{"id":1}"""
                "/api/health" -> 200 to """{"version":"13.2.2"}"""
                else -> 200 to "[]"
            }
        }.use { stub ->
            val basic = server(stub.baseUrl, AuthKind.BASIC).copy(username = "admin")
            GrafanaClient(Http()).fetch(basic, "hunter2")
        }
        assertEquals(1, authHeaders.size)
        val expected = "Basic " + java.util.Base64.getEncoder().encodeToString("admin:hunter2".toByteArray())
        assertEquals(expected, authHeaders.single())
    }
}
