from .models import utcnow

ALLOWED = {
    "open": {"blocked"},
    "assigned": {"in_progress", "open", "blocked"},
    "in_progress": {"done", "blocked"},
    "blocked": {"open"},
    "done": set(),
}
FREES_RESOURCE = {"done", "blocked", "open"}


def can_transition(current: str, new: str) -> bool:
    return new in ALLOWED.get(current, set())


def apply_status(task, resource, new: str) -> None:
    """Mutate task/resource for a status change (caller commits)."""
    now = utcnow()
    was_active = task.status in ("assigned", "in_progress")
    if new == "in_progress":
        task.started_at, task.completed_at = now, None
    if new in ("done", "blocked"):
        task.completed_at = now
    if new == "open":
        task.assigned_to, task.assigned_at = None, None
    if resource is not None and was_active and new in FREES_RESOURCE:
        resource.status = "idle"
    task.status = new
