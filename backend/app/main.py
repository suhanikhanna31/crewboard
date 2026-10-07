import asyncio
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import select

from .assignment import run_once
from .auth import decode_token, hash_pw
from .db import Base, SessionLocal, engine
from .models import Resource, Site, User
from .routers import auth, issues, metrics, resources, tasks
from .ws import manager

AUTO_ASSIGN_INTERVAL = float(os.getenv("AUTO_ASSIGN_INTERVAL", "3"))  # 0 disables (manual baseline)


async def seed() -> None:
    async with SessionLocal() as s:
        if await s.scalar(select(User.id).limit(1)):
            return
        s.add(Site(id=1, name="Main Site"))
        await s.flush()  # no relationship() between models, so insert the site before rows that FK to it
        crew = [("ravi", "Ravi", "rebar,general", "A"), ("asha", "Asha", "drywall,general", "B"),
                ("imran", "Imran", "electrical,general", "C")]
        for username, name, skills, zone in crew:
            res = Resource(name=name, kind="worker", skills=skills, zone=zone)
            s.add(res)
            await s.flush()
            s.add(User(username=username, password_hash=hash_pw("crew123"), role="worker", resource_id=res.id))
        s.add(User(username="supervisor", password_hash=hash_pw("supervisor123"), role="supervisor"))
        await s.commit()


async def assign_loop() -> None:
    while True:
        await asyncio.sleep(AUTO_ASSIGN_INTERVAL)
        try:
            await run_once()
        except Exception as exc:  # keep the loop alive
            print("auto-assign error:", exc)


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    await seed()
    loop = asyncio.create_task(assign_loop()) if AUTO_ASSIGN_INTERVAL > 0 else None
    yield
    if loop:
        loop.cancel()


app = FastAPI(title="CrewBoard", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
for r in (auth, tasks, resources, metrics, issues):
    app.include_router(r.router)


@app.get("/health")
async def health():
    return {"ok": True}


async def save_battery(msg: dict) -> None:
    async with SessionLocal() as s:
        res = await s.get(Resource, msg.get("resource_id"))
        if res and "battery" in msg:
            res.battery = float(msg["battery"])
            await s.commit()


@app.websocket("/ws")
async def ws_endpoint(ws: WebSocket, token: str = ""):
    try:
        decode_token(token)
    except Exception:
        await ws.close(code=4401)
        return
    await manager.connect(ws)
    try:
        while True:
            msg = await ws.receive_json()
            if msg.get("type") == "telemetry":
                await save_battery(msg)
                await manager.broadcast(msg)
            elif msg.get("type") == "ping":
                await ws.send_json({"type": "pong"})
    except WebSocketDisconnect:
        pass
    except Exception:
        pass
    finally:
        manager.disconnect(ws)
