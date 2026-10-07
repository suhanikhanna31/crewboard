# CrewBoard

![CI](https://github.com/suhanikhanna31/crewboard/actions/workflows/ci.yml/badge.svg)

**Demo video (about 90 s):** [CrewBoard: live crew and robot dispatch](https://youtu.be/Gv2yv6TEYa4)

**A live site-crew dispatcher for construction.** Idle workers and misallocated crews are a big part of the labour shortage: if every free person (or robot) is matched to the right job within seconds, fewer people are needed per job. CrewBoard posts jobs, auto-assigns them to the best idle resource, pushes them to the operator's phone in real time, and measures utilisation so the effect is visible.

Humans and robots are the **same resource type**: one dispatcher assigns work to both.

```
[Android app: Kotlin + Compose] <--REST + WebSocket--> [FastAPI + PostgreSQL]
                                                            ^
[React dashboard (supervisor)] <--REST + WebSocket----------|
                                                            |
[Python robot simulator (optional ROS2 bridge)] ------------|
```

## Quick start

```bash
docker compose up --build
```

| What | Where |
|---|---|
| Supervisor dashboard | http://localhost:3000 |
| API + Swagger docs | http://localhost:8000/docs |
| Android app | open `android/` in Android Studio, run on an emulator |

Demo logins: workers `ravi` / `asha` / `imran` (password `crew123`), supervisor `supervisor` / `supervisor123`.

The simulator registers three robots (RebarBot-1/2, DrywallBot-1) and, with `SPAWN_TASKS=1`, posts a new job every few seconds, so the dashboard moves on its own.

**Android:** the default server URL is `http://10.0.2.2:8000` (the host machine from the emulator). On a real phone, change it in the Settings tab to your computer's LAN IP. Log in as `imran`, switch **On shift**, then post an *electrical* job from the dashboard: the phone buzzes, tapping the notification opens that task.

## Android fundamentals, and where to find them

| Fundamental | Where |
|---|---|
| Activity | `MainActivity`: hosts Compose navigation, handles notification deep links in `onCreate`/`onNewIntent` (`singleTop`) |
| Fragment | `ui/SettingsFragment.kt`: a View-based Fragment hosted in Compose via `AndroidView` + `FragmentContainerView` |
| ViewModel + lifecycle | `TaskListViewModel` (`StateFlow`, survives rotation); `collectAsStateWithLifecycle()`; `LifecycleEventEffect(ON_START)` refreshes when the screen returns |
| Coroutines / Flow | `TaskRepository` (suspend REST calls), `SocketClient.events()` (`callbackFlow` + `retryWhen` exponential backoff, cancelled structurally), `viewModelScope` |
| Background service | `service/ShiftService.kt`: foreground service owning the WebSocket during a shift, posts "new task" notifications. `sync/SyncWorker.kt` (WorkManager) replays actions queued offline |
| Jetpack Compose | `ui/Screens.kt`, `ui/CrewBoardRoot.kt`: dark high-contrast theme, 72-88dp buttons for gloves, connection banner, status chips |
| Room | `data/Local.kt`: tasks cache plus a `pending_actions` queue. UI observes Room only |

**HMI details:** LIVE / RECONNECTING / OFFLINE banner, big START / COMPLETE buttons, and a red "Report issue / stop" button that confirms, blocks the task and sends a priority alert to the supervisor.

### Tradeoffs worth talking about
- **Room as single source of truth.** Screens never read from the network directly; network and socket write into Room and the UI reacts. Offline just works and there is no "loading vs cached" state to reconcile.
- **Foreground service owns the socket** (not the ViewModel). A ViewModel dies with the screen; a worker on shift needs notifications with the screen off. The cost is a persistent notification, which is the honest signal that the app is working in the background.
- **Optimistic status changes + queue.** Pressing COMPLETE in a basement with no signal still works; the server stays the arbiter and rejects invalid transitions (HTTP 409), which the client then drops.
- **Hand-rolled service locator** (`CrewApp`) instead of Hilt: less ceremony for a project this size.

## Backend (FastAPI + PostgreSQL)

- REST: `POST /auth/login` (JWT), `POST/GET /tasks`, `PATCH /tasks/{id}/status`, `POST /tasks/{id}/assign`, `GET/POST /resources`, `POST /issues`, `GET /metrics/utilisation`
- WebSocket `/ws?token=...` broadcasts `task_created`, `task_assigned`, `task_updated`, `worker_status`, `telemetry`, `issue`
- Async SQLAlchemy 2.0; Postgres in Docker, SQLite in tests. Indexes on `tasks(status, site_id)`, `tasks(assigned_to)`, `resources(status)`
- A background loop plus event hooks run `auto_assign`: highest priority first; score = skill match, +5 same zone, battery bonus for robots, low-battery robots excluded
- Task state machine: `open -> assigned -> in_progress -> done`, with `blocked` as an escape hatch. Workers can only touch their own tasks

```bash
cd backend && pip install -r requirements.txt && pytest
```

### Show an index helping
`backend/sql/idle_match.sql` is the "idle resources vs open tasks" query. Tested on Postgres 16 with 300,000 seeded tasks (about 1% `open` at `site_id = 1`) and 6 idle resources, after `ANALYZE tasks`, using `EXPLAIN (ANALYZE, BUFFERS)`.

| | With `ix_tasks_status_site` | Without |
|---|---|---|
| Scan on `tasks` | Bitmap Index Scan + Bitmap Heap Scan | Parallel Seq Scan (2 workers) |
| Buffers (shared hit) | 18,061 | 20,947 |
| Execution time | 27.5 ms | 81.2 ms |

The index makes the query about 3x faster. The gain is modest because the query still reads about 3,050 open rows per loop and the join and sort dominate. Single runs on a laptop with everything in cache, so treat these as approximate.

## Measuring the labour-shortage claim

`GET /metrics/utilisation` reports the share of each resource's shift spent actually working, and the dashboard charts it live.

**Result: auto-assignment cut fleet idle time by 17.5% overall, and by 39% across the robots.**

| | Manual dispatch (A) | Auto-assign (B) |
|---|---|---|
| Overall utilisation | 0.093 | 0.252 |
| Overall idle | 0.907 | 0.748 |
| Robot-only utilisation | 0.186 | 0.505 |
| Open tasks at measurement | 109 | 38 |
| Done tasks at measurement | 45 | 114 |

Idle = 1 - utilisation, and reduction = (idle_A - idle_B) / idle_A.

Setup and assumptions:
- Run A: `AUTO_ASSIGN=0 MANUAL_DISPATCH=1 docker compose up --build -d`. A simulated human dispatcher makes one assignment every 20 s.
- Run B: plain `docker compose up --build -d` (auto-assign on).
- Simulated robots; a new job arrives every 6 s (`SPAWN_TASKS=1`, `SPAWN_EVERY=6`).
- The three human workers were held busy in both runs, so the comparison measures robot dispatch only. That is why overall utilisation stays low.
- Measured about 15 minutes after startup in both runs (window of roughly 890-930 s).
- One run per setting, so this is a rough measurement and not a statistically tested result.

## Robotics / edge angle
`simulator/simulator.py` runs three robots that register as resources, stream battery and position telemetry, and perform tasks. `simulator/ros2_bridge.py` is a stretch ROS2 node: it forwards `/crewboard/robot_telemetry` to the backend and publishes assignments on `/crewboard/task_cmd` (needs a ROS2 install; not run in CI).

## CI/CD
- **`ci.yml`** on every PR and push to `main`: pytest, React build, Android unit tests + debug APK (uploaded as an artifact)
- **`release.yml`** on push to `main`: builds the three Docker images and pushes them to GHCR

## Project layout
```
backend/     FastAPI app, tests, SQL
dashboard/   React (Vite) supervisor UI
simulator/   robot fleet simulator + ROS2 bridge
android/     Kotlin / Jetpack Compose operator app
.github/     CI and image publishing
```

## Debugging story: the phone said "On shift" but never connected

**Symptom.** After resetting the stack and logging in again on the emulator, the phone sat on the grey OFFLINE banner and no jobs arrived. Login worked. Switching the shift off and on fixed it.

**Trace.**
1. API log: `POST /auth/login 200`, `GET /resources 200`, `GET /tasks?assigned_to=3 200`, and no `/ws` line. REST was reaching the server, but no WebSocket was ever attempted.
2. `SocketClient.kt`: the connection state becomes `OFFLINE` only as its initial value and in `onCompletion`. During a failing reconnect it is `RECONNECTING`. A stuck OFFLINE therefore meant `events()` was not running at all, not that it was failing.
3. `grep ShiftService`: the foreground service that owns the socket is started in only two places, the On shift toggle and Save and Reconnect (and only `if (s.onShift)`). Nothing started it when the app launched.

**Root cause.** The toggle draws its state from a saved setting (`mutableStateOf(settings.onShift)`), but the socket's lifetime belongs to a service that only the toggle starts. The two can disagree, so the screen could read "On shift" with no service and no socket running.

**Fix.** Restore the service on launch, in `MainActivity.onCreate`:

```kotlin
if (savedInstanceState == null) {
    val s = CrewApp.instance.settings
    if (s.onShift && !s.token.isNullOrEmpty()) ShiftService.start(this)
}
```

I put it in the Activity and not in `Application.onCreate`, because Android 12+ can refuse to start a foreground service from a process that was started in the background (for example by WorkManager).

**What I got wrong first.** My first two theories were the exponential backoff and a silent socket close. Reading the code ruled both out: the backoff caps at 30 s, and `onClosed` and `onFailure` both close the channel with an exception, so `retryWhen` always fires.

**What I'd do differently.** Make the service the single source of truth for shift state and have the UI observe it, so the two cannot drift apart. Add a test that relaunches the app with `onShift = true` and asserts a socket opens.
