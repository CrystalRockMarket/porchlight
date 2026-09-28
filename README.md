Porchlight
=========

*The light you leave on for the stack in the spare room.*

A read-only Android client for infrastructure you already run yourself.

Porchlight connects to **your own** Grafana and Portainer instances and shows their
current state on your phone: firing alerts, container health, and which
environments are down. No account, no analytics, no telemetry.

Brought to you by Kituwa IT — <https://kituwa.com>

---

## What it is

Porchlight is a **viewer, not a monitor**. Your existing servers do the watching;
this app shows you what they report.

That distinction is deliberate. An Android app cannot be a reliable always-on
monitor: Doze mode, App Standby, background execution limits, and OEM battery
optimisers will all stop it, and a phone that silently stops checking is worse
than one that never checked. So Porchlight does not pretend. It displays the
signal your infrastructure produces, and it is explicit about the freshness of
that signal.

## Features

- One summary across every configured server: active problem count, verified
  servers, servers not yet checked
- **Grafana** — server health, version, and active alerts with severity,
  summary, and start time
- **Portainer** — container state, Docker health check results, and offline
  environments
- Background refresh every 30 minutes via WorkManager, with a low-priority
  notification when something needs attention
- Test-connection button before you save, so you find out immediately whether
  your token works
- Plain HTTP supported, so a reverse proxy with a self-signed certificate is fine

## Honest about silence

This is the part that matters most, and it drove most of the design.

If a server cannot be reached, Porchlight **cannot** distinguish that from "the
cluster is on fire". Showing a confident green checkmark for a server that
happened to stop responding is the most dangerous thing a monitoring client can
do. So instead:

- the last known state is kept on-device and shown with a timestamp
- unreachable servers are labelled "Cannot reach", never "All clear"
- the detail screen states plainly that current state is unknown
- an unreachable server counts as a problem in the summary, not a neutral one

The same applies to absences. A Grafana that returns zero alerts because your
token lacks `alerts:read` is not a healthy Grafana, so the app says
"unavailable" for that section instead of "0".

## Privacy

- No account, no sign-up, no analytics, no crash reporting
- No network requests to anything except the servers you configure
- Credentials are encrypted on-device with AES-GCM via the Android Keystore and
  are never transmitted anywhere else
- Nothing is stored on a Kituwa IT server, because there isn't one
- `allowBackup` is disabled, so credentials do not leave the device via backups

## Getting started

### Grafana

1. Administration → Users and access → Service accounts → Add service account
2. Grant it the **Viewer** role
3. Add the `alerts:read` permission (otherwise alerts show as unavailable)
4. Copy the token and paste it as the API token

### Portainer

1. Use your ordinary Portainer username and password — Porchlight exchanges them
   for a short-lived JWT via `/api/auth`
2. A pasted JWT also works, but it expires, so credentials are usually easier

## Build from source

Requires JDK 17+ and the Android SDK (compileSdk 35).

```bash
export ANDROID_HOME=/path/to/Android/sdk
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # unsigned release APK
```

No signing config is checked in. F-Droid builds and signs the app itself, which
is the intended path for this project.

## Testing

60 unit tests, all runnable offline. They exist because testing found real bugs
that reading the code did not.

```bash
./gradlew :app:testDebugUnitTest
```

Two live integration tests are skipped unless you point them at a real server:

```bash
docker run --rm -d --name gf -p 3300:3000 -e GF_SECURITY_ADMIN_USER=admin \
  -e GF_SECURITY_ADMIN_PASSWORD=admin grafana/grafana:latest
STACKMATE_GRAFANA_URL=http://localhost:3300 STACKMATE_GRAFANA_TOKEN=glsa_... \
  ./gradlew :app:testDebugUnitTest
```

Bugs these caught, each of which would have shipped:

- Basic auth sent a doubled `Basic Basic ...` header, breaking every Basic-auth server
- A bad Grafana token looked like success, because `/api/health` is unauthenticated
- Test connection sent `Bearer ` with an empty token, because the secret was read back
  out of the store for a server that had not been saved yet — it failed for every user
- The summary banner said "Everything looks healthy" while a server was unreachable,
  because it counted alerts but not unreachability
- The detail screen claimed "could not be reached" about a server that had just
  answered and reported three firing alerts
- "Last successful check" displayed the time of the failed attempt
- The alert count included resolved alerts
- Container state parsing crashed when Docker reported `State` as an object
- The Material 3 colour scheme was incomplete, so buttons and cards rendered
  baseline purple instead of the app's own colours

The decisions those bugs lived in are now pure functions with no Android
dependencies, so they cannot drift: `summarize()` in `Summary.kt` decides what the
top-line banner claims, and `stalenessWarning()` in `Staleness.kt` decides when
cached data is labelled as such.

## Private and self-signed certificates

The normal case for self-hosted infrastructure is a certificate nobody on the
public web trusts: a reverse proxy with a private CA, or Portainer's own
self-signed certificate.

Porchlight handles this the way Android documents for talking to private
infrastructure: the app opts in to user-installed certificate authorities via
[`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml).
The user installs their CA once, and the certificate then validates normally.

Deliberately **not** implemented: a per-server "accept this certificate" or
"ignore certificate errors" switch. Those disable certificate validation, which
is the protection TLS gives against an active attacker on the same network, and
they are the sort of feature that becomes a security complaint later. Trusting
user-installed CAs gives the same convenience with validation intact, because
the user has positively identified the authority by installing it.

Plain HTTP is also permitted, for services reached over a LAN or VPN. That
decision is declared in the same file rather than via `usesCleartextTraffic`, so
it is visible to a reviewer and can be tightened without touching the manifest.

## Tech

Kotlin, Jetpack Compose with Material 3, OkHttp, kotlinx.serialization, DataStore
Preferences, WorkManager, and a hand-rolled AES-GCM wrapper over the Android
Keystore (no third-party crypto dependency). minSdk 24, targetSdk 35.

No proprietary dependencies — no Google Play Services, no Firebase, no
analytics SDK. That is a hard requirement, both technically and for F-Droid
eligibility.

## Notes for maintainers and reviewers

[NOTES.md](NOTES.md) covers the architecture, the reasoning behind the honesty
rules, the security posture, exactly what was verified against real servers and
what was not, and the known rough edges. Read it before changing the summary
logic in `ui/Summary.kt` or `ui/Staleness.kt`.

## Screenshots

`fastlane/metadata/android/en-US/images/phoneScreenshots/` holds six captures taken
from the running app: the overview with a mix of healthy and failing servers, the
Grafana alert list, the Portainer container list, the honest "showing last known
data" screen for an unreachable server, the About screen, and the add-server form.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).

You are free to inspect it, change it, and run your own copy. If you find a bug,
the fix is welcome.
