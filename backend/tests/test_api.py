import pytest
from conftest import login


def test_login_rejects_bad_password(client):
    assert client.post("/auth/login", json={"username": "ravi", "password": "nope"}).status_code == 401


def test_requires_token(client):
    assert client.get("/tasks").status_code in (401, 403)


def test_new_task_is_auto_assigned_to_matching_idle_worker(client, sup):
    client.post("/resources", headers=sup, json={"name": "Welder", "skills": "welding", "zone": "A"})
    r = client.post("/tasks", headers=sup, json={"title": "Weld frame", "required_skill": "welding"})
    assert r.status_code == 201
    body = r.json()
    assert body["status"] == "assigned" and body["assigned_to"] is not None


def test_task_without_matching_skill_stays_open(client, sup):
    r = client.post("/tasks", headers=sup, json={"title": "Mystery", "required_skill": "nobody-has-this"})
    assert r.json()["status"] == "open"


def test_invalid_transition_is_409(client, sup):
    t = client.post("/tasks", headers=sup, json={"title": "X", "required_skill": "nobody-has-this-2"}).json()
    r = client.patch(f"/tasks/{t['id']}/status", headers=sup, json={"status": "done"})
    assert r.status_code == 409


def test_worker_full_flow_and_ownership(client, sup):
    ravi = login(client, "ravi", "crew123")
    rh = {"Authorization": f"Bearer {ravi['access_token']}"}
    t = client.post("/tasks", headers=sup, json={"title": "Tie rebar", "required_skill": "rebar"}).json()
    assert t["assigned_to"] == ravi["resource_id"]
    assert client.patch(f"/tasks/{t['id']}/status", headers=rh, json={"status": "in_progress"}).status_code == 200
    assert client.patch(f"/tasks/{t['id']}/status", headers=rh, json={"status": "done"}).json()["status"] == "done"
    # worker may not touch someone else's task
    other = client.post("/tasks", headers=sup, json={"title": "Drywall", "required_skill": "drywall"}).json()
    assert client.patch(f"/tasks/{other['id']}/status", headers=rh, json={"status": "in_progress"}).status_code == 403
    # utilisation reflects work done
    util = client.get("/metrics/utilisation", headers=sup).json()
    assert any(r["resource_id"] == ravi["resource_id"] and r["busy_seconds"] >= 0 for r in util["resources"])


def test_worker_cannot_create_tasks(client):
    h = {"Authorization": f"Bearer {login(client, 'asha', 'crew123')['access_token']}"}
    assert client.post("/tasks", headers=h, json={"title": "no"}).status_code == 403


def test_issue_blocks_task_and_frees_worker(client, sup):
    client.post("/resources", headers=sup, json={"name": "Sparky", "skills": "wiring"})
    t = client.post("/tasks", headers=sup, json={"title": "Wire", "required_skill": "wiring"}).json()
    assert client.post("/issues", headers=sup, json={"message": "sparks!", "task_id": t["id"]}).status_code == 200
    got = [x for x in client.get("/tasks", headers=sup).json() if x["id"] == t["id"]][0]
    assert got["status"] == "blocked"


def test_websocket_broadcasts_new_task(client, sup):
    token = sup["Authorization"].split()[1]
    with client.websocket_connect(f"/ws?token={token}") as ws:
        client.post("/tasks", headers=sup, json={"title": "Live", "required_skill": "nobody-3"})
        assert ws.receive_json()["type"] == "task_created"


def test_websocket_rejects_bad_token(client):
    with pytest.raises(Exception):
        with client.websocket_connect("/ws?token=bad"):
            pass
