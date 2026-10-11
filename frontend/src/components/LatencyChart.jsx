const W = 720;
const H = 230;
const PAD = { l: 56, r: 14, t: 14, b: 30 };

function niceCeil(value) {
  const steps = [50, 100, 200, 250, 500, 1000, 2000, 2500, 5000, 10000, 20000, 30000];
  return steps.find((s) => s >= value) ?? Math.ceil(value / 1000) * 1000;
}

function tickLabel(ms, spanHours) {
  const d = new Date(ms);
  return spanHours <= 24
    ? d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })
    : d.toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

/** Response time over time. Failed checks are red marks; the line breaks across them. */
export default function LatencyChart({ points, since, hours }) {
  if (!points || points.length < 2) {
    return <p className="px-1 py-8 text-sm text-muted">Not enough checks in this window to draw a trend yet.</p>;
  }
  const t0 = new Date(since).getTime();
  const t1 = Math.max(Date.now(), new Date(points[points.length - 1].t).getTime());
  const okPoints = points.filter((p) => p.success && p.ms != null);
  const maxMs = niceCeil(Math.max(50, ...okPoints.map((p) => p.ms)) * 1.1);
  const x = (t) => PAD.l + ((new Date(t).getTime() - t0) / Math.max(1, t1 - t0)) * (W - PAD.l - PAD.r);
  const y = (ms) => PAD.t + (1 - ms / maxMs) * (H - PAD.t - PAD.b);

  let path = "";
  let pen = false;
  for (const p of points) {
    if (p.success && p.ms != null) {
      path += `${pen ? "L" : "M"}${x(p.t).toFixed(1)} ${y(p.ms).toFixed(1)} `;
      pen = true;
    } else {
      pen = false;
    }
  }

  const failures = points.filter((p) => !p.success);
  const gridValues = [0, 0.25, 0.5, 0.75, 1].map((f) => Math.round(maxMs * f));
  const ticks = [0, 0.5, 1].map((f) => t0 + (t1 - t0) * f);

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      className="w-full"
      role="img"
      aria-label={`Response time over the last ${hours} hours: ${okPoints.length} successful checks and ${failures.length} failures`}
    >
      {gridValues.map((v) => (
        <g key={v}>
          <line x1={PAD.l} x2={W - PAD.r} y1={y(v)} y2={y(v)} className="stroke-rule" strokeWidth="1" />
          <text x={PAD.l - 8} y={y(v) + 4} textAnchor="end" className="fill-muted" fontSize="11">
            {v >= 1000 ? `${v / 1000}s` : `${v}ms`}
          </text>
        </g>
      ))}
      {failures.map((p, i) => (
        <rect key={i} x={x(p.t) - 1.5} y={PAD.t} width="3" height={H - PAD.t - PAD.b} className="fill-down" opacity="0.35">
          <title>{`Failed at ${new Date(p.t).toLocaleString()}`}</title>
        </rect>
      ))}
      <path d={path} fill="none" className="stroke-signal" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
      {okPoints.length <= 60 &&
        okPoints.map((p, i) => (
          <circle key={i} cx={x(p.t)} cy={y(p.ms)} r="2.5" className="fill-signal">
            <title>{`${p.ms} ms at ${new Date(p.t).toLocaleString()}`}</title>
          </circle>
        ))}
      {ticks.map((t, i) => (
        <text key={i} x={x(t)} y={H - 8} textAnchor={i === 0 ? "start" : i === 2 ? "end" : "middle"} className="fill-muted" fontSize="11">
          {tickLabel(t, hours)}
        </text>
      ))}
    </svg>
  );
}
