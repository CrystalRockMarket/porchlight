package it.kituwa.porchlight.ui

import it.kituwa.porchlight.data.Reachability

/**
 * The staleness notice is the app's core promise: never let cached data look
 * current. It must appear when we could not get fresh data, and must NOT appear
 * when the server answered fine but happens to have critical alerts — an earlier
 * version keyed this off the status colour and shouted "could not be reached"
 * about a server that had just reported three firing alerts.
 */
fun stalenessWarning(reachability: Reachability): String? = when (reachability) {
    Reachability.UNREACHABLE ->
        "Showing last known data. This server could not be reached, so its current state is " +
            "unknown. Silence here does not mean everything is fine."
    Reachability.AUTH_FAILED ->
        "Showing last known data. Porchlight cannot authenticate with this server any more, so " +
            "its current state is unknown."
    Reachability.UNKNOWN ->
        "No successful check yet. Nothing here is confirmed current."
    Reachability.REACHABLE -> null
}
