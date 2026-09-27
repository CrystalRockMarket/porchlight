package it.kituwa.stackmate

import it.kituwa.stackmate.data.AuthKind
import it.kituwa.stackmate.data.Http
import it.kituwa.stackmate.data.PortainerClient
import it.kituwa.stackmate.data.Reachability
import it.kituwa.stackmate.data.Severity
import it.kituwa.stackmate.data.Server
import it.kituwa.stackmate.data.ServerType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortainerClientTest {

    private fun server(baseUrl: String, auth: AuthKind = AuthKind.BASIC) = Server(
        id = "1",
        name = "portainer",
        type = ServerType.PORTAINER,
        baseUrl = baseUrl,
        authKind = auth,
        username = "kituwa",
        secret = "Str0ng-Passw0rd!",
    )

    private fun container(
        name: String,
        state: String,
        status: String = "Up 2 days",
        health: String? = null,
    ): String {
        val stateField = if (health == null) "" else ""","State":{"Status":"$state","Health":"$health"}"""
        return """{"Id":"id-$name","Names":["/$name"],"Image":"library/$name:latest",""" +
            """"State":"$state","Status":"$status"$stateField}"""
    }

    private fun stubPortainer(
        containers: String = "[" + container("nginx", "running", health = "healthy") + "]",
        endpointStatus: Int = 1,
        record: MutableList<StubRequest>? = null,
    ) = StubServer { request ->
        record?.add(request)
        when {
            request.path == "/api/auth" -> 200 to """{"jwt":"jwt-abc-123","username":"kituwa"}"""
            request.path == "/api/endpoints" ->
                200 to """[{"Id":1,"Name":"local","Status":$endpointStatus,"Type":2,"URL":"unix:///var/run/docker.sock"}]"""
            request.path == "/api/endpoints/1/docker/containers/json" -> 200 to containers
            else -> 404 to """{"message":"not found"}"""
        }
    }

    @Test
    fun exchangesCredentialsForJwtThenListsContainers() = runBlocking {
        val requests = mutableListOf<StubRequest>()
        stubPortainer(record = requests).use { stub ->
            val snapshot = PortainerClient(Http()).fetch(server(stub.baseUrl), "Str0ng-Passw0rd!")

            assertEquals(Reachability.REACHABLE, snapshot.reachability)
            val authRequest = requests.first { it.path == "/api/auth" }
            assertEquals("POST", authRequest.method)
            assertTrue(authRequest.body.contains("\"username\":\"kituwa\""))
            assertTrue(authRequest.body.contains("\"password\":\"Str0ng-Passw0rd!\""))

            val containerRequest = requests.first { it.path.contains("containers/json") }
            assertEquals("Bearer jwt-abc-123", containerRequest.authorization)
            assertTrue(containerRequest.fullPath.endsWith("?all=true"))

            val nginx = snapshot.containers.single()
            assertEquals("nginx", nginx.name)
            assertEquals("local", nginx.endpoint)
            assertTrue(nginx.isHealthy)
            assertTrue(snapshot.issues.isEmpty())
            assertEquals("1/1 running", snapshot.details.first { it.first == "Containers" }.second)
        }
    }

    @Test
    fun stoppedContainerBecomesAWarningIssue() = runBlocking {
        val containers = "[" + container("nginx", "running", health = "healthy") + "," +
            container("redis", "exited", status = "Exited (0) 3 minutes ago") + "]"
        stubPortainer(containers = containers).use { stub ->
            val snapshot = PortainerClient(Http()).fetch(server(stub.baseUrl), "pw")

            val issue = snapshot.issues.single()
            assertEquals("redis", issue.title)
            assertEquals(Severity.WARNING, issue.severity)
            assertTrue(issue.detail!!.contains("local"))
        }
    }

    @Test
    fun unhealthyContainerBecomesCritical() = runBlocking {
        stubPortainer(containers = "[" + container("api", "running", health = "unhealthy") + "]")
            .use { stub ->
                val snapshot = PortainerClient(Http()).fetch(server(stub.baseUrl), "pw")
                val issue = snapshot.issues.single()
                assertEquals(Severity.CRITICAL, issue.severity)
                assertTrue(issue.detail!!.contains("health UNHEALTHY"))
                assertFalse(snapshot.containers.single().isHealthy)
            }
    }

    @Test
    fun offlineEnvironmentIsCritical() = runBlocking {
        stubPortainer(endpointStatus = 0).use { stub ->
            val snapshot = PortainerClient(Http()).fetch(server(stub.baseUrl), "pw")

            val issue = snapshot.issues.single()
            assertEquals(Severity.CRITICAL, issue.severity)
            assertEquals("Environment offline", issue.title)
            assertEquals("local", issue.detail)
            assertEquals("0/1 online", snapshot.details.first { it.first == "Environments" }.second)
        }
    }

    @Test
    fun emptyEnvironmentListIsReportedRatherThanShownAsHealthy() = runBlocking {
        StubServer { request ->
            when (request.path) {
                "/api/auth" -> 200 to """{"jwt":"jwt-abc-123"}"""
                "/api/endpoints" -> 200 to "[]"
                else -> 404 to "{}"
            }
        }.use { stub ->
            val error = runCatching { PortainerClient(Http()).fetch(server(stub.baseUrl), "pw") }
                .exceptionOrNull()
            assertTrue(error is it.kituwa.stackmate.data.ReachabilityException)
            assertTrue(error!!.message!!.contains("No Portainer environments"))
        }
    }

    @Test
    fun preSuppliedJwtIsUsedWithoutLoggingIn() = runBlocking {
        val requests = mutableListOf<StubRequest>()
        stubPortainer(record = requests).use { stub ->
            val jwtServer = server(stub.baseUrl, AuthKind.TOKEN).copy(secret = "jwt-abc-123")
            PortainerClient(Http()).fetch(jwtServer, "jwt-abc-123")

            assertTrue("must not call /api/auth when a JWT is supplied", requests.none { it.path == "/api/auth" })
            assertEquals("Bearer jwt-abc-123", requests.first { it.path.contains("containers/json") }.authorization)
        }
    }

    @Test
    fun loginFailureIsAuthFailure() = runBlocking {
        StubServer { request ->
            if (request.path == "/api/auth") 401 to """{"message":"Unauthorized"}""" else 404 to "{}"
        }.use { stub ->
            val error = runCatching { PortainerClient(Http()).fetch(server(stub.baseUrl), "bad") }
                .exceptionOrNull()
            assertTrue(error is it.kituwa.stackmate.data.HttpException)
        }
    }

    @Test
    fun responseWithoutJwtIsTreatedAsAuthFailure() = runBlocking {
        StubServer { request ->
            if (request.path == "/api/auth") 200 to """{"username":"kituwa"}""" else 404 to "{}"
        }.use { stub ->
            val error = runCatching { PortainerClient(Http()).fetch(server(stub.baseUrl), "pw") }
                .exceptionOrNull()
            assertTrue(error is it.kituwa.stackmate.data.ReachabilityException)
            assertEquals(
                Reachability.AUTH_FAILED,
                (error as it.kituwa.stackmate.data.ReachabilityException).reachability,
            )
        }
    }
}
