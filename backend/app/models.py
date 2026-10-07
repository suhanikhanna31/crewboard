from datetime import datetime, timezone

from sqlalchemy import DateTime, Float, ForeignKey, Index, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from .db import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


class Site(Base):
    __tablename__ = "sites"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))


class Resource(Base):
    """A workable unit: a human worker or a robot. Same table on purpose."""
    __tablename__ = "resources"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100), unique=True)
    kind: Mapped[str] = mapped_column(String(10), default="worker")  # worker | robot
    skills: Mapped[str] = mapped_column(String(200), default="general")  # csv
    zone: Mapped[str] = mapped_column(String(20), default="A")
    status: Mapped[str] = mapped_column(String(10), default="idle")  # idle | busy
    battery: Mapped[float] = mapped_column(Float, default=100.0)
    site_id: Mapped[int] = mapped_column(ForeignKey("sites.id"), default=1)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    __table_args__ = (Index("ix_resources_status", "status"),)

    @property
    def skill_list(self) -> list[str]:
        return [s.strip() for s in self.skills.split(",") if s.strip()]


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(50), unique=True)
    password_hash: Mapped[str] = mapped_column(String(200))
    role: Mapped[str] = mapped_column(String(20), default="worker")  # worker | supervisor
    resource_id: Mapped[int | None] = mapped_column(ForeignKey("resources.id"), nullable=True)


class Task(Base):
    __tablename__ = "tasks"
    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str] = mapped_column(String(1000), default="")
    required_skill: Mapped[str] = mapped_column(String(50), default="general")
    zone: Mapped[str] = mapped_column(String(20), default="A")
    priority: Mapped[int] = mapped_column(Integer, default=1)
    # open | assigned | in_progress | done | blocked
    status: Mapped[str] = mapped_column(String(15), default="open")
    site_id: Mapped[int] = mapped_column(ForeignKey("sites.id"), default=1)
    assigned_to: Mapped[int | None] = mapped_column(ForeignKey("resources.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    assigned_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    started_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    __table_args__ = (
        Index("ix_tasks_status_site", "status", "site_id"),
        Index("ix_tasks_assigned_to", "assigned_to"),
    )
