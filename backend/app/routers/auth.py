from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select

from ..auth import make_token, verify_pw
from ..db import get_session
from ..models import User
from ..schemas import LoginIn, TokenOut

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/login", response_model=TokenOut)
async def login(body: LoginIn, s=Depends(get_session)):
    user = await s.scalar(select(User).where(User.username == body.username))
    if not user or not verify_pw(body.password, user.password_hash):
        raise HTTPException(401, "Wrong username or password")
    return TokenOut(access_token=make_token(user), role=user.role, resource_id=user.resource_id)
