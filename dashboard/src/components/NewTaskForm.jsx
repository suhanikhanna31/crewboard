import { useState } from "react";

const SKILLS = ["rebar", "drywall", "electrical", "general"];

export default function NewTaskForm({ onCreate }) {
  const [title, setTitle] = useState("");
  const [skill, setSkill] = useState("general");
  const [zone, setZone] = useState("A");
  const [priority, setPriority] = useState(1);

  const submit = async (e) => {
    e.preventDefault();
    if (!title.trim()) return;
    await onCreate({ title: title.trim(), required_skill: skill, zone, priority: Number(priority) });
    setTitle("");
  };

  return (
    <form className="new" onSubmit={submit}>
      <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="New job, e.g. Tie rebar level 2" />
      <select value={skill} onChange={(e) => setSkill(e.target.value)}>{SKILLS.map((s) => <option key={s}>{s}</option>)}</select>
      <select value={zone} onChange={(e) => setZone(e.target.value)}>{"ABC".split("").map((z) => <option key={z}>{z}</option>)}</select>
      <select value={priority} onChange={(e) => setPriority(e.target.value)}>
        {[1, 2, 3, 4, 5].map((p) => <option key={p} value={p}>priority {p}</option>)}
      </select>
      <button>Post job</button>
    </form>
  );
}
