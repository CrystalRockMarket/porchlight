package it.kituwa.porchlight

import it.kituwa.porchlight.data.Issue
import it.kituwa.porchlight.data.Reachability
import it.kituwa.porchlight.data.Severity
import it.kituwa.porchlight.data.Server
import it.kituwa.porchlight.data.ServerSnapshot
import it.kituwa.porchlight.data.ServerType
import it.kituwa.porchlight.ui.stalenessWarning
import it.kituwa.porchlight.ui.summarize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the worst bug this app could ship: telling the user
 * "Everything looks healthy" while a server was down. An unreachable server
 * reports no issues, so counting issues alone silently declared outages healthy.
 */
class SummaryTest {

    private fun server(id: String) = Server(
        id = id,
        name = "server-$id",
        type = ServerType.GRAFANA,
        baseUrl = "https://$id.example.com",
        authKind = it.kituwa.porchlight.data.AuthKind.TOKEN,
    )

    private fun snapshot(
        id: String,
        reachability: Reachability = Reachability.REACHABLE,
        issues: List<Issue> = emptyList(),
    ) = ServerSnapshot(
        serverId = id,
        reachability = reachability,
        issues = issues,
        observedAt = 0L,
    )

    @Test
    fun noServersIsNotHealthy() {
        val summary = summarize(emptyList(), emptyMap())
        assertEquals("No servers yet", summary.headline)
        assertFalse(summary.healthy)
    }

    @Test
    fun nothingConfiguredIsNotAnAlarm() {
        assertFalse("an empty app is not an emergency", summarize(emptyList(), emptyMap()).alarming)
    }

    @Test
    fun stillCheckingIsNotAnAlarm() {
        assertFalse(summarize(listOf(server("a")), emptyMap()).alarming)
    }

    @Test
    fun anOutageIsAnAlarm() {
        val summary = summarize(listOf(server("a")), mapOf("a" to snapshot("a", Reachability.UNREACHABLE)))
        assertTrue(summary.alarming)
    }

    @Test
    fun allHealthyIsNotAnAlarm() {
        val summary = summarize(listOf(server("a")), mapOf("a" to snapshot("a")))
        assertFalse(summary.alarming)
    }

    @Test
    fun reachableServerWithNoIssuesIsHealthy() {
        val summary = summarize(listOf(server("a")), mapOf("a" to snapshot("a")))
        assertEquals("Everything looks healthy", summary.headline)
        assertTrue(summary.healthy)
    }

    @Test
    fun unreachableServerIsNeverReportedAsHealthy() {
        val summary = summarize(
            listOf(server("a")),
            mapOf("a" to snapshot("a", Reachability.UNREACHABLE)),
        )
        assertFalse(summary.healthy)
        assertEquals("Needs attention", summary.headline)
        assertTrue(summary.detail.contains("not reporting"))
    }

    @Test
    fun authFailureIsNeverReportedAsHealthy() {
        val summary = summarize(
            listOf(server("a")),
            mapOf("a" to snapshot("a", Reachability.AUTH_FAILED)),
        )
        assertFalse(summary.healthy)
    }

    @Test
    fun unverifiedServerIsNeverReportedAsHealthy() {
        val summary = summarize(listOf(server("a")), emptyMap())
        assertFalse(summary.healthy)
        assertEquals("Checking your server", summary.headline)
    }

    @Test
    fun oneBrokenServerAmongHealthyOnesIsStillNotHealthy() {
        val summary = summarize(
            listOf(server("a"), server("b")),
            mapOf(
                "a" to snapshot("a"),
                "b" to snapshot("b", Reachability.UNREACHABLE),
            ),
        )
        assertFalse(summary.healthy)
        assertEquals("Needs attention", summary.headline)
    }

    @Test
    fun activeAlertsAreCounted() {
        val summary = summarize(
            listOf(server("a")),
            mapOf(
                "a" to snapshot(
                    "a",
                    issues = listOf(Issue(Severity.CRITICAL, "DatabaseDown")),
                ),
            ),
        )
        assertEquals("Needs attention", summary.headline)
        assertTrue(summary.detail.contains("1 active problem"))
    }

    @Test
    fun outagesAndAlertsAreBothMentioned() {
        val summary = summarize(
            listOf(server("a"), server("b")),
            mapOf(
                "a" to snapshot("a", Reachability.UNREACHABLE),
                "b" to snapshot("b", issues = listOf(Issue(Severity.WARNING, "DiskFilling"))),
            ),
        )
        assertTrue(summary.detail.contains("not reporting"))
        assertTrue(summary.detail.contains("1 active problem"))
    }

    @Test
    fun partialVerificationIsNotHealthy() {
        val summary = summarize(listOf(server("a"), server("b")), mapOf("a" to snapshot("a")))
        assertFalse(summary.healthy)
    }
}

/**
 * The detail screen once shouted "This server could not be reached" about a server
 * that had just answered and reported three firing alerts, because the notice was
 * keyed off the status colour instead of whether fresh data was actually obtained.
 */
class StalenessWarningTest {

    @Test
    fun reachableServerNeverShowsAStalenessNotice() {
        assertNull(stalenessWarning(Reachability.REACHABLE))
    }

    @Test
    fun unreachableServerIsWarnedExplicitly() {
        val notice = stalenessWarning(Reachability.UNREACHABLE)!!
        assertTrue(notice.contains("does not mean everything is fine"))
    }

    @Test
    fun authFailureIsWarnedExplicitly() {
        assertTrue(stalenessWarning(Reachability.AUTH_FAILED)!!.contains("unknown"))
    }

    @Test
    fun noDataYetIsWarned() {
        assertTrue(stalenessWarning(Reachability.UNKNOWN)!!.contains("No successful check"))
    }
}
