"""STRETCH: ROS2 <-> CrewBoard bridge (untested without a ROS2 install).
Subscribes to /crewboard/robot_telemetry (std_msgs/String JSON) and forwards it to the
backend WebSocket; publishes backend task assignments on /crewboard/task_cmd.
Run inside a ROS2 Humble/Jazzy environment: python3 ros2_bridge.py"""
import asyncio
import json
import os
import threading

import httpx
import rclpy
import websockets
from rclpy.node import Node
from std_msgs.msg import String

API = os.getenv("API_URL", "http://localhost:8000")


class Bridge(Node):
    def __init__(self, token: str):
        super().__init__("crewboard_bridge")
        self.token, self.out = token, None
        self.cmd_pub = self.create_publisher(String, "/crewboard/task_cmd", 10)
        self.create_subscription(String, "/crewboard/robot_telemetry", self.on_telemetry, 10)
        self.loop = asyncio.new_event_loop()
        threading.Thread(target=self.loop.run_until_complete, args=(self.ws_main(),), daemon=True).start()

    def on_telemetry(self, msg: String):
        if self.out:
            asyncio.run_coroutine_threadsafe(self.out.send(json.dumps({"type": "telemetry", **json.loads(msg.data)})), self.loop)

    async def ws_main(self):
        async with websockets.connect(API.replace("http", "ws", 1) + f"/ws?token={self.token}") as ws:
            self.out = ws
            async for raw in ws:
                m = json.loads(raw)
                if m["type"] == "task_assigned":
                    self.cmd_pub.publish(String(data=json.dumps(m["task"])))


def main():
    token = httpx.post(f"{API}/auth/login", json={"username": "supervisor", "password": "supervisor123"}).json()["access_token"]
    rclpy.init()
    rclpy.spin(Bridge(token))


if __name__ == "__main__":
    main()
