from fastapi import APIRouter, Depends
from sqlalchemy import select

from ..auth import current_user, require_supervisor
from ..db import get_session
from ..models import Resource
from ..schemas import ResourceIn, ResourceOut
from ..ws import manager, resource_msg

router = APIRouter(prefix="/resources", tags=["resources"])


@router.get("", response_model=list[ResourceOut])
async def list_resources(s=Depends(get_session), _=Depends(current_user)):
    return (await s.scalars(select(Resource).order_by(Resource.kind, Resource.name))).all()


@router.post("", response_model=ResourceOut)
async def upsert_resource(body: ResourceIn, s=Depends(get_session), _=Depends(require_supervisor)):
    """Idempotent by name, so a restarting robot simulator re-registers safely."""
    res = await s.scalar(select(Resource).where(Resource.name == body.name))
    if res:
        res.kind, res.skills, res.zone = body.kind, body.skills, body.zone
    else:
        res = Resource(**body.model_dump())
        s.add(res)
    await s.commit()
    await s.refresh(res)
    await manager.broadcast(resource_msg(res))
    return res
