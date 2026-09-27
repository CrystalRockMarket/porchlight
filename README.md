StackMate
=========

A read-only Android client for infrastructure you already run yourself.

StackMate connects to **your own** Grafana and Portainer instances and shows their
current state on your phone: firing alerts, container health, and which
environments are down. No account, no analytics, no telemetry.

Brought to you by Kituwa IT — <https://kituwa.com>

---

## What it is

StackMate is a **viewer, not a monitor**. Your existing servers do the watching;
this app shows you what they report.

That distinction is deliberate. An Android app cannot be a reliable always-on
monitor: Doze mode, App Standby, background execution limits, and OEM battery
optimisers will all stop it, and a phone that silently stops checking is worse
than one that never checked. So StackMate does not pretend. It displays the
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

If a server cannot be reached, StackMate **cannot** distinguish that from "the
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

1. Use your ordinary Portainer username and password — StackMate exchanges them
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

## Tech

Kotlin, Jetpack Compose with Material 3, OkHttp, kotlinx.serialization, DataStore
Preferences, WorkManager, and a hand-rolled AES-GCM wrapper over the Android
Keystore (no third-party crypto dependency). minSdk 24, targetSdk 35.

No proprietary dependencies — no Google Play Services, no Firebase, no
analytics SDK. That is a hard requirement, both technically and for F-Droid
eligibility.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).

You are free to inspect it, change it, and run your own copy. If you find a bug,
the fix is welcome.
