const COLUMNS = [
  ["open", "Waiting"],
  ["assigned", "Assigned"],
  ["in_progress", "Working"],
  ["done", "Done"],
  ["blocked", "Blocked"],
];

export default function TaskBoard({ tasks, resources }) {
  const nameOf = (id) => resources.find((r) => r.id === id)?.name ?? "—";
  return (
    <section className="board">
      {COLUMNS.map(([status, label]) => {
        const items = tasks.filter((t) => t.status === status);
        return (
          <div key={status} className={`col col-${status}`}>
            <h3>{label} <span className="count">{items.length}</span></h3>
            {items.slice(0, 12).map((t) => (
              <article
                key={t.id}
                className={`card p${t.priority}`}
                draggable={status === "open" || status === "assigned"}
                onDragStart={(e) => e.dataTransfer.setData("text/task", String(t.id))}
              >
                <strong>{t.title}</strong>
                <small>{t.required_skill} · zone {t.zone}{t.assigned_to ? ` · ${nameOf(t.assigned_to)}` : ""}</small>
              </article>
            ))}
          </div>
        );
      })}
    </section>
  );
}
