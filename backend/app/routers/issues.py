from fastapi import APIRouter, Depends
from sqlalchemy import select

from ..assignment import run_once
from ..auth import current_user
from ..db import get_session
from ..models import Resource, Task
from ..schemas import IssueIn
from ..services import apply_status, can_transition
from ..ws import manager, resource_msg, task_msg

router = APIRouter(prefix="/issues", tags=["issues"])


@router.post("")
async def report_issue(body: IssueIn, s=Depends(get_session), user=Depends(current_user)):
    """Emergency stop / report issue: priority broadcast, and blocks the task if one is given."""
    task = await s.get(Task, body.task_id) if body.task_id else None
    res = None
    if task and can_transition(task.status, "blocked"):
        res = await s.get(Resource, task.assigned_to) if task.assigned_to else None
        apply_status(task, res, "blocked")
        await s.commit()
        await manager.broadcast(task_msg("task_updated", task))
        if res:
            await manager.broadcast(resource_msg(res))
    await manager.broadcast({"type": "issue", "message": body.message, "task_id": body.task_id,
                             "by": user["sub"], "priority": "high"})
    if res:
        await run_once()
    return {"ok": True}
