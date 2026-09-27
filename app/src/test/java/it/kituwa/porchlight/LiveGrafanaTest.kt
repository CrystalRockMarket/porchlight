package it.kituwa.porchlight

import it.kituwa.porchlight.data.AuthKind
import it.kituwa.porchlight.data.GrafanaClient
import it.kituwa.porchlight.data.Http
import it.kituwa.porchlight.data.Reachability
import it.kituwa.porchlight.data.Server
import it.kituwa.porchlight.data.ServerType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs against a real Grafana when STACKMATE_GRAFANA_URL and STACKMATE_GRAFANA_TOKEN
 * are set, and is skipped otherwise. This is what caught the double "Basic " prefix
 * and the annotations.summary lookup that a hand-written payload got wrong.
 */
class LiveGrafanaTest {

    private val url = System.getenv("STACKMATE_GRAFANA_URL")
    private val token = System.getenv("STACKMATE_GRAFANA_TOKEN")

    private fun server() = Server(
        id = "live",
        name = "live-grafana",
        type = ServerType.GRAFANA,
        baseUrl = Http().normalizeUrl(url!!),
        authKind = AuthKind.TOKEN,
        secret = token!!,
    )

    @Test
    fun realServiceAccountTokenReadsHealthAndAlerts() = runBlocking {
        assumeTrue("set STACKMATE_GRAFANA_URL to run", url != null)
        assumeTrue("set STACKMATE_GRAFANA_TOKEN to run", token != null)

        val snapshot = GrafanaClient(Http()).fetch(server(), token!!)

        assertEquals(Reachability.REACHABLE, snapshot.reachability)
        val version = snapshot.details.first { it.first == "Grafana" }.second
        assertTrue("expected a real version, got '$version'", version.isNotBlank() && version != "unknown")
        assertTrue(
            "alerts must be readable with a Viewer service account",
            snapshot.details.any { it.first == "Firing" && it.second != "unavailable" },
        )
    }

    @Test
    fun realGrafanaRejectsABadToken() = runBlocking {
        assumeTrue("set STACKMATE_GRAFANA_URL to run", url != null)

        val bad = server().copy(secret = "glsa_definitely_not_valid")
        val error = runCatching { GrafanaClient(Http()).fetch(bad, "glsa_definitely_not_valid") }
            .exceptionOrNull()
        assertTrue("expected an auth failure, got $error", error != null)
    }
}
