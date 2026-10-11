import { displayMetricValue } from "./MetricsPanel.jsx";

const W = 420;
const H = 96;
const PAD = { l: 4, r: 4, t: 8, b: 8 };

function pointValue(name, value) {
  if (name.includes("cpu.usage")) return value * 100;
  return value;
}

export default function MetricTrend({ name, points }) {
  if (points.length < 2) return <p className="mt-3 text-xs text-muted">Collecting trend data…</p>;

  const values = points.map((point) => pointValue(name, point.value));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = Math.max(max - min, max === 0 ? 1 : Math.abs(max) * 0.05);
  const x = (index) => PAD.l + (index / (points.length - 1)) * (W - PAD.l - PAD.r);
  const y = (value) => PAD.t + (1 - (value - (min - range * 0.1)) / (range * 1.2)) * (H - PAD.t - PAD.b);
  const path = points.map((point, index) => `${index === 0 ? "M" : "L"}${x(index).toFixed(1)} ${y(pointValue(name, point.value)).toFixed(1)}`).join(" ");
  const latest = points[points.length - 1];

  return (
    <div className="mt-3">
      <svg viewBox={`0 0 ${W} ${H}`} className="h-24 w-full" role="img" aria-label={`${name} trend`}>
        <line x1="0" x2={W} y1={H - PAD.b} y2={H - PAD.b} className="stroke-rule" />
        <path d={path} fill="none" className="stroke-signal" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx={x(points.length - 1)} cy={y(pointValue(name, latest.value))} r="3.5" className="fill-signal" />
      </svg>
      <div className="flex items-center justify-between text-xs text-muted">
        <span>{displayMetricValue(name, latest.value)}</span>
        <span>{points.length} samples</span>
      </div>
    </div>
  );
}
