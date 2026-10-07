# CrewBoard

![CI](https://github.com/suhanikhanna31/crewboard/actions/workflows/ci.yml/badge.svg)

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
`backend/sql/idle_match.sql` is the "idle resources vs open tasks" query. In Postgres:
```sql
EXPLAIN ANALYZE <contents of idle_match.sql>;
```
Seed a few thousand tasks first (e.g. with `generate_series`) and compare the plan with and without `ix_tasks_status_site`. Paste the before/after into this README.

## Measuring the labour-shortage claim

`GET /metrics/utilisation` reports the share of each resource's shift spent actually working, and the dashboard charts it live.

To get an honest "idle time reduced by X%" number:
1. Run with `AUTO_ASSIGN=0 MANUAL_DISPATCH=1 docker compose up --build -d` (no auto-assign: a simulated human dispatcher makes one assignment every 20s). Let the simulator run 10 minutes, record overall utilisation.
2. Run again with plain `docker compose up --build -d` (auto-assign on). Record it again.
3. Put both numbers, and the setup, here.

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

## Debugging story (fill this in)
Pick one real bug you hit and trace it end to end: Android screen, then API, then simulator. This is the story to tell in the interview.
