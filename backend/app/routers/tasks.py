from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select

from ..assignment import run_once
from ..auth import current_user, require_supervisor
from ..db import get_session
from ..models import Resource, Task, utcnow
from ..schemas import AssignIn, StatusIn, TaskIn, TaskOut
from ..services import apply_status, can_transition
from ..ws import manager, resource_msg, task_msg

router = APIRouter(prefix="/tasks", tags=["tasks"])


@router.post("", response_model=TaskOut, status_code=201)
async def create_task(body: TaskIn, s=Depends(get_session), _=Depends(require_supervisor)):
    task = Task(**body.model_dump())
    s.add(task)
    await s.commit()
    await s.refresh(task)
    await manager.broadcast(task_msg("task_created", task))
    await run_once()
    await s.refresh(task)
    return task


@router.get("", response_model=list[TaskOut])
async def list_tasks(status: str | None = None, site_id: int | None = None,
                     assigned_to: int | None = None, s=Depends(get_session), _=Depends(current_user)):
    q = select(Task).order_by(Task.priority.desc(), Task.id)
    if status:
        q = q.where(Task.status == status)
    if site_id:
        q = q.where(Task.site_id == site_id)
    if assigned_to:
        q = q.where(Task.assigned_to == assigned_to)
    return (await s.scalars(q)).all()


@router.patch("/{task_id}/status", response_model=TaskOut)
async def set_status(task_id: int, body: StatusIn, s=Depends(get_session), user=Depends(current_user)):
    task = await s.get(Task, task_id)
    if not task:
        raise HTTPException(404, "Task not found")
    is_sup = user["role"] == "supervisor"
    if not is_sup and (user.get("rid") != task.assigned_to or body.status == "open"):
        raise HTTPException(403, "Not your task")
    if not can_transition(task.status, body.status):
        raise HTTPException(409, f"Cannot go from {task.status} to {body.status}")
    res = await s.get(Resource, task.assigned_to) if task.assigned_to else None
    apply_status(task, res, body.status)
    await s.commit()
    await manager.broadcast(task_msg("task_updated", task))
    if res:
        await manager.broadcast(resource_msg(res))
    if body.status in ("done", "blocked", "open"):
        await run_once()
    return task


@router.post("/{task_id}/assign", response_model=TaskOut)
async def assign(task_id: int, body: AssignIn, s=Depends(get_session), _=Depends(require_supervisor)):
    task, res = await s.get(Task, task_id), await s.get(Resource, body.resource_id)
    if not task or not res:
        raise HTTPException(404, "Task or resource not found")
    if task.status not in ("open", "assigned"):
        raise HTTPException(409, "Task already started or finished")
    if res.status != "idle":
        raise HTTPException(409, f"{res.name} is busy")
    prev = await s.get(Resource, task.assigned_to) if task.assigned_to else None
    if prev:
        prev.status = "idle"
    task.status, task.assigned_to, task.assigned_at = "assigned", res.id, utcnow()
    res.status = "busy"
    await s.commit()
    await manager.broadcast(task_msg("task_assigned", task))
    for r in (prev, res):
        if r:
            await manager.broadcast(resource_msg(r))
    return task
