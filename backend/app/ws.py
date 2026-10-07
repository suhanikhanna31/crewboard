from fastapi import WebSocket

from .schemas import ResourceOut, TaskOut


class Manager:
    def __init__(self) -> None:
        self.conns: set[WebSocket] = set()

    async def connect(self, ws: WebSocket) -> None:
        await ws.accept()
        self.conns.add(ws)

    def disconnect(self, ws: WebSocket) -> None:
        self.conns.discard(ws)

    async def broadcast(self, msg: dict) -> None:
        for conn in list(self.conns):
            try:
                await conn.send_json(msg)
            except Exception:
                self.disconnect(conn)


manager = Manager()


def task_msg(kind: str, task) -> dict:
    return {"type": kind, "task": TaskOut.model_validate(task).model_dump(mode="json")}


def resource_msg(resource) -> dict:
    return {"type": "worker_status", "resource": ResourceOut.model_validate(resource).model_dump(mode="json")}
