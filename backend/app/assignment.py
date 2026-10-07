"""Auto-assignment: the 'labour shortage' logic. Maximise utilisation by never
leaving a capable idle resource next to an open task."""
import os

from sqlalchemy import select

from .db import SessionLocal
from .models import Resource, Task, utcnow
from .ws import manager, resource_msg, task_msg

MIN_BATTERY = 20.0
ENABLED = os.getenv("AUTO_ASSIGN", "1") == "1"  # AUTO_ASSIGN=0 -> manual-dispatch baseline


def score(res, task, load: int = 0) -> float | None:
    """Higher is better; None means ineligible."""
    if task.required_skill not in res.skill_list:
        return None
    if res.kind == "robot" and res.battery < MIN_BATTERY:
        return None
    s = 10.0
    if res.zone == task.zone:
        s += 5.0
    if res.kind == "robot":
        s += res.battery / 100.0  # prefer fuller batteries
    return s - 2.0 * load


def best_match(resources, task):
    scored = [(score(r, task), r) for r in resources]
    scored = [(s, r) for s, r in scored if s is not None]
    return max(scored, key=lambda p: p[0])[1] if scored else None


async def auto_assign(session) -> list[tuple[Task, Resource]]:
    idle = list((await session.scalars(select(Resource).where(Resource.status == "idle"))).all())
    open_tasks = (await session.scalars(
        select(Task).where(Task.status == "open").order_by(Task.priority.desc(), Task.created_at))).all()
    made = []
    for task in open_tasks:
        res = best_match(idle, task)
        if res is None:
            continue
        task.status, task.assigned_to, task.assigned_at = "assigned", res.id, utcnow()
        res.status = "busy"
        idle.remove(res)
        made.append((task, res))
    await session.commit()
    return made


async def run_once() -> int:
    if not ENABLED:
        return 0
    async with SessionLocal() as session:
        made = await auto_assign(session)
    for task, res in made:
        await manager.broadcast(task_msg("task_assigned", task))
        await manager.broadcast(resource_msg(res))
    return len(made)
