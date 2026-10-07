from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

Status = Literal["open", "assigned", "in_progress", "done", "blocked"]


class LoginIn(BaseModel):
    username: str
    password: str


class TokenOut(BaseModel):
    access_token: str
    role: str
    resource_id: int | None = None


class TaskIn(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str = ""
    required_skill: str = "general"
    zone: str = "A"
    priority: int = Field(default=1, ge=1, le=5)
    site_id: int = 1


class TaskOut(TaskIn):
    model_config = ConfigDict(from_attributes=True)
    id: int
    status: Status
    assigned_to: int | None = None
    created_at: datetime
    started_at: datetime | None = None
    completed_at: datetime | None = None


class StatusIn(BaseModel):
    status: Status


class AssignIn(BaseModel):
    resource_id: int


class ResourceIn(BaseModel):
    name: str
    kind: Literal["worker", "robot"] = "worker"
    skills: str = "general"
    zone: str = "A"


class ResourceOut(ResourceIn):
    model_config = ConfigDict(from_attributes=True)
    id: int
    status: str
    battery: float


class IssueIn(BaseModel):
    message: str = Field(min_length=1, max_length=500)
    task_id: int | None = None
