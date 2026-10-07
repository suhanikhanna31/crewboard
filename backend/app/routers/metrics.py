from datetime import timedelta

from fastapi import APIRouter, Depends
from sqlalchemy import select

from ..auth import current_user
from ..db import get_session
from ..models import Resource, Task, utcnow

router = APIRouter(prefix="/metrics", tags=["metrics"])


@router.get("/utilisation")
async def utilisation(hours: float = 8, s=Depends(get_session), _=Depends(current_user)):
    """% of time each resource spent actively working (started -> done/blocked)."""
    now = utcnow()
    resources = (await s.scalars(select(Resource))).all()
    tasks = (await s.scalars(select(Task).where(Task.started_at.is_not(None)))).all()
    rows, total_busy, total_window = [], 0.0, 0.0
    for r in resources:
        window = max(1.0, min(hours * 3600, (now - r.created_at).total_seconds()))
        start = now - timedelta(seconds=window)
        busy = 0.0
        for t in tasks:
            if t.assigned_to == r.id:
                busy += max(0.0, ((t.completed_at or now) - max(t.started_at, start)).total_seconds())
        busy = min(busy, window)
        total_busy, total_window = total_busy + busy, total_window + window
        rows.append({"resource_id": r.id, "name": r.name, "kind": r.kind,
                     "busy_seconds": round(busy), "window_seconds": round(window),
                     "utilisation": round(busy / window, 3)})
    return {"overall": round(total_busy / total_window, 3) if total_window else 0.0, "resources": rows}
