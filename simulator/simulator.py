"""Robot fleet simulator. Robots are the same 'resource' type as human workers:
they register via REST, stream telemetry over WebSocket, and pick up assigned tasks.
Set SPAWN_TASKS=1 to also generate a steady stream of jobs (hands-free demo)."""
import asyncio
import json
import os
import random

import httpx
import websockets

API = os.getenv("API_URL", "http://localhost:8000")
WS = API.replace("http", "ws", 1)
USER, PASSWORD = os.getenv("SIM_USER", "supervisor"), os.getenv("SIM_PASS", "supervisor123")
SPAWN = os.getenv("SPAWN_TASKS", "0") == "1"
SPAWN_EVERY = float(os.getenv("SPAWN_EVERY", "6"))
MANUAL = os.getenv("MANUAL_DISPATCH", "0") == "1"
MANUAL_DELAY = float(os.getenv("MANUAL_DELAY", "20"))  # seconds per assignment: the slow human dispatcher
ROBOTS = [("RebarBot-1", "rebar,general", "A"), ("RebarBot-2", "rebar,general", "B"),
          ("DrywallBot-1", "drywall,general", "B")]
JOBS = {"rebar": "Tie rebar mat", "drywall": "Board partition wall",
        "electrical": "Pull conduit", "general": "Clear debris"}

robots: dict[int, dict] = {}
background: set[asyncio.Task] = set()


def spawn(coro) -> None:
    t = asyncio.create_task(coro)
    background.add(t)
    t.add_done_callback(background.discard)


async def login(http: httpx.AsyncClient) -> str:
    while True:
        try:
            r = await http.post("/auth/login", json={"username": USER, "password": PASSWORD})
            r.raise_for_status()
            return r.json()["access_token"]
        except Exception as exc:
            print("waiting for API...", exc)
            await asyncio.sleep(2)


def tick(r: dict) -> None:
    if r["state"] == "working":
        r["battery"] = max(0.0, r["battery"] - 1.5)
        r["x"] += random.uniform(-1, 1)
        r["y"] += random.uniform(-1, 1)
    else:
        r["battery"] = min(100.0, r["battery"] + 2.0)
        r["state"] = "charging" if r["battery"] < 20 else "idle"


async def work(http: httpx.AsyncClient, task: dict) -> None:
    r = robots[task["assigned_to"]]
    await asyncio.sleep(random.uniform(1, 2))
    if task["status"] == "assigned":
        await http.patch(f"/tasks/{task['id']}/status", json={"status": "in_progress"})
    r["state"] = "working"
    print(f"{r['name']} working on #{task['id']} {task['title']}")
    await asyncio.sleep(random.uniform(8, 16))
    await http.patch(f"/tasks/{task['id']}/status", json={"status": "done"})
    r["state"] = "idle"


async def session(http: httpx.AsyncClient, token: str) -> None:
    async with websockets.connect(f"{WS}/ws?token={token}") as ws:
        print("websocket connected")

        async def sender():
            while True:
                for rid, r in robots.items():
                    tick(r)
                    await ws.send(json.dumps({"type": "telemetry", "resource_id": rid,
                                              "battery": round(r["battery"], 1), "x": round(r["x"], 2),
                                              "y": round(r["y"], 2), "state": r["state"]}))
                await asyncio.sleep(2)

        async def receiver():
            async for raw in ws:
                m = json.loads(raw)
                if m["type"] == "task_assigned" and m["task"]["assigned_to"] in robots:
                    spawn(work(http, m["task"]))

        await asyncio.gather(sender(), receiver())


async def spawner(http: httpx.AsyncClient) -> None:
    while True:
        await asyncio.sleep(SPAWN_EVERY)
        skill = random.choice(list(JOBS))
        await http.post("/tasks", json={"title": f"{JOBS[skill]} ({random.randint(1, 99)})",
                                        "required_skill": skill, "zone": random.choice("ABC"),
                                        "priority": random.randint(1, 3)})


async def manual_dispatcher(http: httpx.AsyncClient) -> None:
    """Baseline for the benchmark: a human dispatcher who makes one assignment every
    MANUAL_DELAY seconds. Takes the highest-priority open task and gives it to the first
    idle resource with the right skill (no zone or battery optimisation)."""
    while True:
        await asyncio.sleep(MANUAL_DELAY)
        try:
            tasks = (await http.get("/tasks", params={"status": "open"})).json()
            idle = [r for r in (await http.get("/resources")).json() if r["status"] == "idle"]
            for task in sorted(tasks, key=lambda t: (-t["priority"], t["id"])):
                res = next((r for r in idle if task["required_skill"] in r["skills"].split(",")
                            and not (r["kind"] == "robot" and r["battery"] < 20)), None)
                if res:
                    await http.post(f"/tasks/{task['id']}/assign", json={"resource_id": res["id"]})
                    print(f"manual dispatch: #{task['id']} -> {res['name']}")
                    break  # one assignment per tick
        except Exception as exc:
            print("manual dispatcher error:", exc)


async def main() -> None:
    async with httpx.AsyncClient(base_url=API, timeout=10) as http:
        token = await login(http)
        http.headers["Authorization"] = f"Bearer {token}"
        for name, skills, zone in ROBOTS:
            res = (await http.post("/resources", json={"name": name, "kind": "robot",
                                                       "skills": skills, "zone": zone})).json()
            robots[res["id"]] = {"name": name, "battery": res["battery"], "x": 0.0, "y": 0.0, "state": "idle"}
        # resume work orphaned by a simulator restart
        for t in (await http.get("/tasks")).json():
            if t["assigned_to"] in robots and t["status"] in ("assigned", "in_progress"):
                spawn(work(http, t))
        if SPAWN:
            spawn(spawner(http))
        if MANUAL:
            spawn(manual_dispatcher(http))
        while True:
            try:
                await session(http, token)
            except Exception as exc:
                print("websocket lost, retrying:", exc)
                await asyncio.sleep(3)
                token = await login(http)
                http.headers["Authorization"] = f"Bearer {token}"


if __name__ == "__main__":
    asyncio.run(main())
