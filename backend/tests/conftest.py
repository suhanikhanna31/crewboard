import os
import pathlib

_db = pathlib.Path(__file__).parent / "test.db"
_db.unlink(missing_ok=True)
os.environ["DATABASE_URL"] = f"sqlite+aiosqlite:///{_db}"
os.environ["AUTO_ASSIGN_INTERVAL"] = "0"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402


@pytest.fixture(scope="session")
def client():
    with TestClient(app) as c:
        yield c


def login(client, username, password):
    r = client.post("/auth/login", json={"username": username, "password": password})
    assert r.status_code == 200, r.text
    return r.json()


@pytest.fixture(scope="session")
def sup(client):
    return {"Authorization": f"Bearer {login(client, 'supervisor', 'supervisor123')['access_token']}"}
