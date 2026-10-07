import { useState } from "react";

export default function WorkerList({ resources, onAssign }) {
  const [over, setOver] = useState(null);
  return (
    <aside className="crew">
      <h3>Crew &amp; robots</h3>
      <p className="hint">Drag a waiting task onto an idle resource to assign it.</p>
      <ul>
        {resources.map((r) => (
          <li
            key={r.id}
            className={`${r.status} ${over === r.id ? "over" : ""}`}
            onDragOver={(e) => { e.preventDefault(); setOver(r.id); }}
            onDragLeave={() => setOver(null)}
            onDrop={(e) => {
              e.preventDefault();
              setOver(null);
              const id = Number(e.dataTransfer.getData("text/task"));
              if (id) onAssign(id, r.id);
            }}
          >
            <span className="who">{r.kind === "robot" ? "🤖" : "👷"} {r.name}</span>
            <span className="meta">
              {r.skills} · {r.status}
              {r.kind === "robot" && (
                <span className="batt"><i style={{ width: `${Math.round(r.battery)}%` }} className={r.battery < 20 ? "low" : ""} /></span>
              )}
            </span>
          </li>
        ))}
      </ul>
    </aside>
  );
}
