import { useEffect, useReducer, useState } from "react";
import { api, login, wsUrl } from "./api";
import { useWebSocket } from "./hooks/useWebSocket";
import TaskBoard from "./components/TaskBoard";
import WorkerList from "./components/WorkerList";
import UtilisationChart from "./components/UtilisationChart";
import NewTaskForm from "./components/NewTaskForm";

const upsert = (list, item) => (list.some((x) => x.id === item.id) ? list.map((x) => (x.id === item.id ? item : x)) : [...list, item]);

function reducer(state, m) {
  switch (m.type) {
    case "init": return { ...state, tasks: m.tasks, resources: m.resources };
    case "task_created":
    case "task_updated":
    case "task_assigned": return { ...state, tasks: upsert(state.tasks, m.task) };
    case "worker_status": return { ...state, resources: upsert(state.resources, m.resource) };
    case "telemetry":
      return { ...state, resources: state.resources.map((r) => (r.id === m.resource_id ? { ...r, battery: m.battery } : r)) };
    case "issue": return { ...state, alerts: [{ ...m, key: Date.now() }, ...state.alerts].slice(0, 4) };
    case "dismiss": return { ...state, alerts: state.alerts.filter((a) => a.key !== m.key) };
    default: return state;
  }
}

export default function App() {
  const [state, dispatch] = useReducer(reducer, { tasks: [], resources: [], alerts: [] });
  const [url, setUrl] = useState(null);
  const [util, setUtil] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    (async () => {
      try {
        await login("supervisor", "supervisor123");
        const [tasks, resources] = await Promise.all([api.tasks(), api.resources()]);
        dispatch({ type: "init", tasks, resources });
        setUrl(wsUrl());
      } catch (e) { setError(`Cannot reach the API: ${e.message}`); }
    })();
  }, []);

  useEffect(() => {
    if (!url) return;
    const load = () => api.utilisation().then(setUtil).catch(() => {});
    load();
    const id = setInterval(load, 8000);
    return () => clearInterval(id);
  }, [url]);

  const status = useWebSocket(url, dispatch);
  const assign = (taskId, resourceId) => api.assign(taskId, resourceId).catch((e) => setError(e.message));
  const open = state.tasks.filter((t) => t.status === "open").length;

  return (
    <main>
      <header>
        <h1>CrewBoard</h1>
        <span className={`pill ${status}`}>{status}</span>
        <span className="summary">{open} waiting · {state.resources.filter((r) => r.status === "idle").length} idle</span>
      </header>
      {error && <div className="err" onClick={() => setError("")}>{error}</div>}
      {state.alerts.map((a) => (
        <div className="alert" key={a.key} onClick={() => dispatch({ type: "dismiss", key: a.key })}>
          ⚠ {a.by} reported: {a.message}{a.task_id ? ` (task #${a.task_id})` : ""}
        </div>
      ))}
      <NewTaskForm onCreate={(t) => api.createTask(t).catch((e) => setError(e.message))} />
      <div className="layout">
        <TaskBoard tasks={state.tasks} resources={state.resources} />
        <div className="side">
          <WorkerList resources={state.resources} onAssign={assign} />
          <UtilisationChart data={util} />
        </div>
      </div>
    </main>
  );
}
