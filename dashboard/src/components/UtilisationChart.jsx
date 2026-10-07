export default function UtilisationChart({ data }) {
  const pct = (v) => Math.round(v * 100);
  return (
    <section className="util">
      <h3>Utilisation <span className="big">{data ? `${pct(data.overall)}%` : "…"}</span></h3>
      <p className="hint">Share of the shift each resource spent actually working.</p>
      {data?.resources.map((r) => (
        <div className="bar" key={r.resource_id}>
          <span>{r.name}</span>
          <div className="track"><div className={`fill ${r.kind}`} style={{ width: `${pct(r.utilisation)}%` }} /></div>
          <b>{pct(r.utilisation)}%</b>
        </div>
      ))}
    </section>
  );
}
