package it.kituwa.stackmate

import it.kituwa.stackmate.data.Container
import it.kituwa.stackmate.data.Issue
import it.kituwa.stackmate.data.Reachability
import it.kituwa.stackmate.data.Severity
import it.kituwa.stackmate.data.ServerSnapshot
import it.kituwa.stackmate.ui.ServerStatus
import it.kituwa.stackmate.ui.statusOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTest {

    private fun snapshot(
        reachability: Reachability = Reachability.REACHABLE,
        issues: List<Issue> = emptyList(),
    ) = ServerSnapshot(
        serverId = "1",
        reachability = reachability,
        issues = issues,
        observedAt = 0L,
    )

    @Test
    fun missingSnapshotIsUnknownNotHealthy() {
        assertEquals(ServerStatus.UNKNOWN, statusOf(null))
    }

    @Test
    fun reachableWithoutIssuesIsOk() {
        assertEquals(ServerStatus.OK, statusOf(snapshot()))
    }

    @Test
    fun unreachableIsNeverReportedAsOk() {
        val status = statusOf(snapshot(Reachability.UNREACHABLE))
        assertEquals(ServerStatus.UNREACHABLE, status)
        assertTrue(status != ServerStatus.OK)
    }

    @Test
    fun authFailureIsCritical() {
        assertEquals(ServerStatus.CRITICAL, statusOf(snapshot(Reachability.AUTH_FAILED)))
    }

    @Test
    fun worstSeverityWins() {
        val issues = listOf(
            Issue(Severity.WARNING, "disk"),
            Issue(Severity.CRITICAL, "database down"),
        )
        assertEquals(ServerStatus.CRITICAL, statusOf(snapshot(issues = issues)))
    }

    @Test
    fun infoSeverityStillSurfacesAsWarning() {
        val issues = listOf(Issue(Severity.INFO, "informational"))
        assertEquals(ServerStatus.WARNING, statusOf(snapshot(issues = issues)))
    }

    @Test
    fun runningContainerWithoutHealthCheckIsHealthy() {
        val container = Container("web", "nginx", "running", "Up 2 days", null, "prod")
        assertTrue(container.isHealthy)
    }

    @Test
    fun runningContainerWithUnhealthyCheckIsNotHealthy() {
        val container = Container("web", "nginx", "running", "Up 2 days", "UNHEALTHY", "prod")
        assertFalse(container.isHealthy)
    }

    @Test
    fun exitedContainerIsNotHealthy() {
        val container = Container("web", "nginx", "exited", "Exited (1)", null, "prod")
        assertFalse(container.isHealthy)
    }
}
