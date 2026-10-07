import hashlib
import hmac
import os
import time

import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

SECRET = os.getenv("JWT_SECRET", "dev-secret-change-me-please-use-32-bytes-min")
bearer = HTTPBearer()


def hash_pw(password: str, salt: str | None = None) -> str:
    salt = salt or os.urandom(8).hex()
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 60_000).hex()
    return f"{salt}${digest}"


def verify_pw(password: str, stored: str) -> bool:
    salt = stored.split("$", 1)[0]
    return hmac.compare_digest(hash_pw(password, salt), stored)


def make_token(user) -> str:
    payload = {"sub": user.username, "role": user.role, "rid": user.resource_id,
               "exp": int(time.time()) + 12 * 3600}
    return jwt.encode(payload, SECRET, algorithm="HS256")


def decode_token(token: str) -> dict:
    return jwt.decode(token, SECRET, algorithms=["HS256"])


async def current_user(creds: HTTPAuthorizationCredentials = Depends(bearer)) -> dict:
    try:
        return decode_token(creds.credentials)
    except jwt.PyJWTError:
        raise HTTPException(401, "Invalid or expired token")


async def require_supervisor(user: dict = Depends(current_user)) -> dict:
    if user["role"] != "supervisor":
        raise HTTPException(403, "Supervisor only")
    return user
