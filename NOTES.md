# Porchlight — Engineering Notes

Everything a maintainer, reviewer, or F-Droid auditor needs to know that isn't
obvious from the code. Written after building, running, and auditing the app
against real Grafana and Portainer instances.

- **App:** Porchlight 1.0.0 (versionCode 1)
- **Package:** `it.kituwa.porchlight`
- **License:** GPL-3.0-or-later
- **Min SDK:** 24 · **Target/Compile SDK:** 35
- **Toolchain:** AGP 8.9.2, Gradle 8.11.1, Kotlin 2.0.21, JDK 17 target

---

## 1. What it is, and what it deliberately is not

Porchlight is a **read-only client** for infrastructure the user already runs.
It reads Grafana and Portainer and shows status. It never writes to them.

The distinction that shaped the whole design: **an Android app cannot be a
reliable monitor.** Doze mode, App Standby, background execution limits, and
OEM battery optimisation all stop it. A phone that silently stops checking is
worse than one that never checked, because it produces false reassurance.

So Porchlight never claims to monitor. It displays the signal the user's own
infrastructure produces, and is explicit about how fresh that signal is. The
store copy states this outright rather than burying it.

### The one rule everything else serves

**Never let cached data look current.**

Every honest-messaging decision traces back to it:

| Situation | What a naive client does | What Porchlight does |
|---|---|---|
| Server unreachable | Shows last-known data, green | "Cannot reach", red, counted as a problem |
| Never checked | Blank or "healthy" | "No data yet", never healthy |
| Token can't read alerts | Reports "0 alerts" | Reports "alavailable" ≠ zero |
| Last check | Timestamp of last *attempt* | Timestamp of last *success* |
| Grafana token revoked | "Authentication rejected" | Distinct message with the real fix |

---

## 2. The honesty rules live in pure functions

The two worst bugs in the project were both in *decisions*, not logic, and unit
tests on the components did not catch either. So the decisions are now
Android-free, side-effect-free functions with tests over every combination.

- `ui/Summary.kt` → `summarize()` decides what the top-line banner claims
- `ui/Staleness.kt` → `stalenessWarning()` decides when cached data is labelled

Changing a colour or a string cannot reintroduce a lie. If you add a new state,
add it to both functions and their tests in the same commit.

---

## 3. Architecture

```
ui/          Compose screens + one ViewModel (PorchlightViewModel)
  Summary.kt      pure: banner headline/detail/healthy/alarming
  Staleness.kt    pure: staleness notice per Reachability
  Components.kt   StatusDot, MetaRow, statusOf()
data/        models, HTTP, per-backend adapters, stores
  PorchlightClient  orchestrates: read secret → fetch → classify → cache
  GrafanaClient    /api/user, /api/health, /api/alertmanager/.../v2/alerts
  PortainerClient  /api/auth, /api/endpoints, .../docker/containers/json
  Crypto           AES-256-GCM, key in AndroidKeyStore
  ServerStore      DataStore: server configs, secrets encrypted at rest
  SnapshotStore    DataStore: last snapshot per server, for offline display
work/        WorkManager 30-min refresh + notification
```

### Adding a backend

Implement a class with a `fetch(server, secret): ServerSnapshot` method and add
one `when` branch in `PorchlightClient.refresh()`. Nothing else needs to change:
the UI renders whatever `issues`, `containers`, and `details` the snapshot
carries.

**Read-only rule:** the only HTTP verbs in the codebase are `GET` and one
`POST` to Portainer's `/api/auth` to exchange credentials for a short-lived
JWT. `AuditFixTest.onlyGetAndAuthPostAreEverUsed` enforces this. A new adapter
must not introduce a mutating call.

---

## 4. Backend specifics worth knowing

### Grafana

- **`/api/health` is unauthenticated.** A bad token still returns 200, so health
  alone cannot prove anything. Porchlight probes **`/api/user`** first, which
  requires auth, and reports `AUTH_FAILED` on 401/403. This was a real bug: a
  revoked token looked like a healthy server.
- Alert summaries live in `annotations.summary`, not a top-level `message`.
- `status` is `firing`, `pending`, or `OK`; only firing alerts become issues.
- A **Viewer** service account can read `/api/alertmanager/grafana/api/v2/alerts`
  (verified against Grafana 13.2.2). The `alerts:read` permission is still
  recommended, and a 403 is surfaced as "unavailable", never as zero.

### Portainer

- Credentials are exchanged for a JWT at `/api/auth`. The JWT is held in memory
  only and never persisted; the password is what gets encrypted at rest.
- Docker reports `State` as a **string** in the list endpoint but as an
  **object** in some versions. Both are handled; assuming one crashes the parse.
- Health is in `State.Health` when present, otherwise inferred from the
  `(healthy)` / `(unhealthy)` suffix in the human-readable `Status` string.
- **A failed container list is indistinguishable from an environment with zero
  containers.** A dead Docker socket used to be reported as healthy. It now
  raises a critical "Could not read containers" issue and the count reads
  "unknown". This is the same silence-as-health trap as everywhere else.

---

## 5. Security posture

| Concern | Decision |
|---|---|
| Credential storage | AES-256-GCM, key in `AndroidKeyStore`, per-install |
| Credential in logs | Never logged; snapshot contains no secret |
| Backups | `allowBackup="false"` so credentials don't leave via backup |
| TLS | Full validation. **No trust-all switch**, by design |
| Private CAs | Opted in via `network_security_config.xml` (Android standard) |
| Cleartext | Permitted, declared in that config rather than the manifest |
| Network calls | Only to servers the user configured |
| Analytics | None. Not a single dependency that phones home |
| Permissions | `INTERNET`, `POST_NOTIFICATIONS`. Nothing else |

`ACCESS_NETWORK_STATE` was declared and unused; removed during the audit. An
unnecessary permission on a privacy-branded app is a credibility problem with
F-Droid reviewers.

### Why no "accept this certificate" toggle

A per-server bypass would silently disable certificate validation, which is the
protection TLS gives against an active attacker on the same network. The app
instead opts in to **user-installed CAs**, the approach Android documents for
private infrastructure: the user installs their CA once and validation stays
intact. See §7 for how far this was verified.

---

## 6. Testing

**67 unit tests, all runnable offline.** Every one of these defects was found by
testing or by running the app, not by reading the code:

| Defect | Found by |
|---|---|
| Basic auth sent a doubled `Basic Basic …` header | Test |
| Container state parsing crashed on object-shaped `State` | Test |
| Test connection sent an empty `Bearer` token, failing for every user | Running it |
| Banner said "healthy" while a server was down | Running it |
| Detail screen said "could not be reached" about a reachable server | Running it |
| "Last successful check" showed the failed attempt's time | Running it |
| Resolved alerts counted as firing | Test |
| Material 3 scheme incomplete → baseline purple buttons | Running it |
| Notification permission requested at launch | Running it |
| Delete destroyed a server on one tap, no undo | Running it (a stray tap of mine) |
| Invalid URL save failed silently | Reading |
| Dead Docker socket reported as healthy | Reading |
| `secretFor()` read via a write transaction | Reading |
| `upsert()` could double-encrypt a secret | Reading |
| Undecryptable Keystore entry looked like a bad token | Reading |
| Crashing Navigation lint detectors (broke `./gradlew check`) | Clean clone |

```bash
./gradlew :app:testDebugUnitTest
```

Live integration tests are opt-in and skip cleanly without a server:

```bash
docker run -d --name gf -p 3300:3000 \
  -e GF_SECURITY_ADMIN_USER=admin -e GF_SECURITY_ADMIN_PASSWORD=admin grafana/grafana
STACKMATE_GRAFANA_URL=http://localhost:3300 STACKMATE_GRAFANA_TOKEN=glsa_… \
  ./gradlew :app:testDebugUnitTest
```

`LiveGrafanaTest` proves a real service-account token authenticates, that alerts
are readable, and that a bad token is rejected. `LivePortainerTest` is not
written: Portainer 2.45.1 refused a hand-built endpoint-creation payload, so
there was no way to stand up a real environment to test against. Portainer's
coverage is stub-based only, and that is the weakest part of the suite.

---

## 7. What was verified, and what was not

Be precise about this. Overclaiming here is how a project like this gets caught
out by a reviewer.

**Verified by execution:**

- Full flow against **real Grafana 13.2.2** — service-account token, auth probe,
  version, firing alerts with severity and timestamps
- Keystore round-trip — credential saved encrypted, decrypted on refresh
- **Outage path** — killed the container, confirmed "Cannot reach", red banner,
  and an honest last-success timestamp
- Portainer container/environment/health rendering, including a dead-socket case
- Dark mode, empty state, delete confirmation, About, first launch
- **Fresh `git clone` with no `local.properties`:** `check` and `assembleRelease`
  both pass — the exact thing F-Droid does
- **Private-CA server, untrusted:** correctly rejected, confirming no
  verification bypass was introduced
- Merged manifest and packaged resource both declare `system` + `user` anchors

**Not verified:**

- **No real-device test.** Everything ran on emulators. The notification path and
  the Keystore are the two things I would most want confirmed on hardware.
- **The user-CA success path was not executed.** The emulator would not load a
  hand-pushed user certificate (pushing to
  `/data/misc/keychain/cacerts-added` and rebooting did not register it, and the
  `android.credentials.INSTALL` intent is no longer resolvable). The declaration
  is correct and shipped; the behaviour is Android's, not ours. **Test this on a
  real device before telling users it works.**
- **Portainer against a real instance**, for the reason above.
- **No trademark register search** on "Porchlight". Web search found no software
  collision, which is not the same as a clean EUIPO/USPTO search.

---

## 8. Building

```bash
export ANDROID_HOME=/path/to/Android/sdk   # platform 35, build-tools 35
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease             # unsigned; F-Droid signs its own
./gradlew check                            # includes lint
```

No signing config is committed, and that is intentional — F-Droid builds and
signs the app itself.

If lint ever crashes with a `NoClassDefFoundError` in a Navigation detector,
that is an AGP bug, not this project. Do not disable detectors one at a time; it
just moves the crash. Bump AGP.

---

## 9. F-Droid submission

**Metadata lives in two places, both committed:**

- `fastlane/metadata/android/en-US/` — fastlane format
- `metadata/android/en-US/` — F-Droid's native layout

Keep them in sync. F-Droid reads the second; the first is for anyone using
fastlane or the Play Store tooling.

| Field | Value |
|---|---|
| License | `GPL-3.0-only` |
| Categories | `System`, `Monitoring` |
| Author | Kituwa IT |
| Icon | `images/icon/icon.png`, 512×512 |
| Screenshots | 6 PNGs under `images/phoneScreenshots/` |

`fdroid/versions.yml` enables automatic build detection on new tags.

**Screenshots** were captured from the running app on a dedicated emulator, with
a window-focus assertion before each capture — a shared emulator kept stealing
focus and producing screenshots of the wrong app. `4-unreachable.png` is the
important one: it demonstrates the honesty behaviour, showing a real
last-successful-check timestamp beside "silence here does not mean everything is
fine".

**Anticipated reviewer question:** the "Brought to you by Kituwa IT" credit in
About. The answer is that the app is free, self-hosted, telemetry-free, and
GPL-licensed, and the credit is attribution rather than promotion. That is a
normal arrangement for an open-source app with a sponsor.

---

## 10. Known rough edges

1. **No edit-server flow.** A server must be removed and re-added to change its
   token. The `upsert()` API was hardened for this (the secret is passed
   separately so double-encryption is not expressible) but no UI uses it yet.
2. **No notification per-server detail.** The notification summarises counts; you
   have to open the app to see which server. Reasonable, but a long list would
   want per-server notifications.
3. **No swipe-to-refresh.** Only a top-bar button. Android users expect the
   gesture.
4. **`isMinifyEnabled = false`.** The release APK is ~11.8 MB. Enabling R8 would
   roughly halve it at the cost of ProGuard risk; worth doing before a second
   backend is added, not before v1.0.0.
5. **Only English.** `resourceConfigurations += listOf("en")` keeps the APK
   small. Translations would be welcome.
6. **No Uptime Kuma support.** Deliberately deferred: it has no public REST API,
   so it would mean reverse-engineering a private Socket.io protocol that breaks
   without notice. The adapter-per-backend structure exists so it can be added
   without touching the rest of the app.

---

## 11. If you are forking this

The three places that carry the product's integrity, in order of importance:

1. `ui/Summary.kt` and `ui/Staleness.kt` — the honesty rules
2. `AuditFixTest.kt` and `SummaryTest.kt` — the tests that guard them
3. `res/xml/network_security_config.xml` — the security posture, with the
   reasoning inline

Change the words and the colours freely. Do not change those three without
understanding why they are the way they are.
