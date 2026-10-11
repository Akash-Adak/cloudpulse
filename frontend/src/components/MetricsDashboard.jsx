import { useEffect, useMemo, useState } from "react";
import { api } from "../lib/api.js";
import { absoluteTime, formatMs, formatPercent } from "../lib/format.js";
import { displayMetricValue } from "./MetricsPanel.jsx";
import { Notice, StatusMark } from "./ui.jsx";

const WINDOWS = [
  { hours: 1, label: "1h" },
  { hours: 6, label: "6h" },
  { hours: 24, label: "24h" },
  { hours: 168, label: "7d" },
];

function metricGroups(points) {
  const groups = new Map();
  for (const point of points || []) {
    if (!groups.has(point.name)) groups.set(point.name, []);
    groups.get(point.name).push(point);
  }
  return [...groups.entries()];
}

function percentile(values, fraction) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * fraction) - 1)];
}

const COLORS = ["#3FD08A", "#52A7FF", "#F2B84B", "#E86A6A"];

// Catmull-Rom -> cubic Bezier, purely so trend lines read as continuous
// motion instead of a strip of straight segments. Same underlying (x, y)
// values as before — this only changes how the path between them is drawn.
function smoothPath(coords) {
  if (coords.length < 2) return "";
  if (coords.length === 2) return `M${coords[0][0]},${coords[0][1]} L${coords[1][0]},${coords[1][1]}`;
  let d = `M${coords[0][0].toFixed(2)},${coords[0][1].toFixed(2)}`;
  for (let i = 0; i < coords.length - 1; i++) {
    const p0 = coords[i === 0 ? 0 : i - 1];
    const p1 = coords[i];
    const p2 = coords[i + 1];
    const p3 = coords[i + 2 < coords.length ? i + 2 : i + 1];
    const c1x = p1[0] + (p2[0] - p0[0]) / 6;
    const c1y = p1[1] + (p2[1] - p0[1]) / 6;
    const c2x = p2[0] - (p3[0] - p1[0]) / 6;
    const c2y = p2[1] - (p3[1] - p1[1]) / 6;
    d += ` C${c1x.toFixed(2)},${c1y.toFixed(2)} ${c2x.toFixed(2)},${c2y.toFixed(2)} ${p2[0].toFixed(2)},${p2[1].toFixed(2)}`;
  }
  return d;
}

function freshnessLabel(seconds) {
  if (seconds == null) return "connecting…";
  if (seconds < 2) return "updated just now";
  if (seconds < 60) return `updated ${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `updated ${minutes}m ago`;
  return `updated ${Math.floor(minutes / 60)}h ago`;
}

function LiveBadge({ lastUpdated, tick, ok = true }) {
  const seconds = lastUpdated != null ? Math.max(0, Math.floor((Date.now() - lastUpdated) / 1000)) : null;
  void tick; // re-renders this badge on each tick without touching fetch logic
  return (
    <div className="flex items-center gap-2 text-xs text-muted">
      <span className="relative flex h-2 w-2">
        {ok && <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-up opacity-70" />}
        <span className={`relative inline-flex h-2 w-2 rounded-full ${ok ? "bg-up" : "bg-down"}`} />
      </span>
      <span className="tabular-nums">{freshnessLabel(seconds)}</span>
    </div>
  );
}

function Panel({ title, type, children }) {
  return (
    <section className="overflow-hidden rounded-lg border border-rule bg-panel shadow-sm">
      <div className="flex items-center justify-between border-b border-rule px-5 py-4">
        <div>
          <h3 className="font-semibold tracking-tight">{title}</h3>
          <p className="mt-0.5 text-xs text-muted">{type}</p>
        </div>
        <span className="h-2 w-2 rounded-full bg-up shadow-[0_0_0_4px_rgba(63,208,138,0.12)]" aria-label="Live chart" />
      </div>
      <div className="p-5">{children}</div>
    </section>
  );
}

function LineChart({ points, value, color = "#52A7FF", label, live = false }) {
  if (!points || points.length < 2) return <EmptyChart />;
  const values = points.map(value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = Math.max(max - min, 1);
  const coords = values.map((v, i) => [
    Number(((i / (values.length - 1)) * 100).toFixed(2)),
    Number((96 - ((v - min) / span) * 78).toFixed(2)),
  ]);
  const path = smoothPath(coords);
  const last = coords[coords.length - 1];
  const gid = `lg-${label.replace(/\s+/g, "-")}`;
  return (
    <svg viewBox="0 0 100 100" className="h-48 w-full overflow-visible" role="img" aria-label={label}>
      <defs>
        <linearGradient id={gid} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.22" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      {[20, 45, 70, 95].map((y) => (
        <line key={y} x1="0" x2="100" y1={y} y2={y} className="stroke-rule" strokeWidth="0.5" />
      ))}
      <path d={`${path} L100 100 L0 100 Z`} fill={`url(#${gid})`} stroke="none" />
      <path d={path} fill="none" stroke={color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      {live && (
        <>
          <circle cx={last[0]} cy={last[1]} r="4" fill={color} opacity="0.25" className="animate-ping" style={{ transformOrigin: `${last[0]}px ${last[1]}px` }} />
          <circle cx={last[0]} cy={last[1]} r="1.6" fill={color} />
        </>
      )}
    </svg>
  );
}

function AreaChart({ points, value, color = "#3FD08A", label }) {
  if (!points || points.length < 2) return <EmptyChart />;
  const values = points.map(value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = Math.max(max - min, 1);
  const coords = values.map((v, i) => [
    Number(((i / (values.length - 1)) * 100).toFixed(2)),
    Number((92 - ((v - min) / span) * 76).toFixed(2)),
  ]);
  const line = smoothPath(coords);
  return (
    <svg viewBox="0 0 100 100" className="h-48 w-full" role="img" aria-label={label}>
      <defs>
        <linearGradient id={`area-${label}`} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.35" />
          <stop offset="100%" stopColor={color} stopOpacity="0.02" />
        </linearGradient>
      </defs>
      <path d={`${line} L100 100 L0 100 Z`} fill={`url(#area-${label})`} />
      <path d={line} fill="none" stroke={color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function EmptyChart() {
  return <div className="flex h-48 items-center justify-center rounded bg-wash text-sm text-muted">Not enough samples yet to draw this chart.</div>;
}

function Histogram({ checks }) {
  const values = checks.filter((check) => check.success && check.responseTimeMs != null).map((check) => check.responseTimeMs);
  if (values.length < 2) return <EmptyChart />;
  const max = Math.max(...values, 1);
  const buckets = Array.from({ length: 8 }, (_, i) => values.filter((value) => value <= ((i + 1) / 8) * max).length - values.filter((value) => value <= (i / 8) * max).length);
  const tallest = Math.max(...buckets, 1);
  return (
    <div className="flex h-48 items-end gap-2 rounded bg-wash px-4 pb-4 pt-5">
      {buckets.map((count, i) => (
        <div key={i} className="flex flex-1 flex-col items-center gap-2">
          <div
            className="w-full rounded-t bg-signal transition-[height] duration-500 ease-out"
            style={{ height: `${Math.max(5, (count / tallest) * 150)}px` }}
            title={`${count} checks`}
          />
          <span className="text-[10px] tabular-nums text-muted">{Math.round(((i + 1) * max) / 8)}ms</span>
        </div>
      ))}
    </div>
  );
}

function Donut({ checks }) {
  const groups = [
    ["2xx", checks.filter((check) => check.statusCode >= 200 && check.statusCode < 300).length],
    ["3xx", checks.filter((check) => check.statusCode >= 300 && check.statusCode < 400).length],
    ["4xx", checks.filter((check) => check.statusCode >= 400 && check.statusCode < 500).length],
    ["5xx", checks.filter((check) => check.statusCode >= 500).length],
  ];
  const total = groups.reduce((sum, [, count]) => sum + count, 0);
  if (!total) return <EmptyChart />;
  let offset = 0;
  return (
    <div className="flex items-center gap-6">
      <div className="relative h-40 w-40 shrink-0">
        <svg viewBox="0 0 42 42" className="h-40 w-40 -rotate-90" role="img" aria-label="HTTP status code distribution">
          <circle cx="21" cy="21" r="15.9" fill="none" stroke="#E8EDF1" strokeWidth="6" />
          {groups.map(([name, count], i) => {
            if (!count) return null;
            const length = (count / total) * 100;
            const segment = (
              <circle
                key={name}
                cx="21"
                cy="21"
                r="15.9"
                fill="none"
                stroke={COLORS[i]}
                strokeWidth="6"
                strokeLinecap="round"
                strokeDasharray={`${Math.max(length - 1.5, 0.5)} ${100 - length + 1.5}`}
                strokeDashoffset={-offset}
              />
            );
            offset += length;
            return segment;
          })}
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-xl font-semibold tabular-nums">{total}</span>
          <span className="text-[11px] text-muted">checks</span>
        </div>
      </div>
      <div className="space-y-2 text-sm">
        {groups.map(([name, count], i) => (
          <p key={name} className="flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: COLORS[i] }} />
            {name}
            <strong className="ml-2 tabular-nums">{count}</strong>
            <span className="text-xs text-muted tabular-nums">{total ? `${Math.round((count / total) * 100)}%` : ""}</span>
          </p>
        ))}
      </div>
    </div>
  );
}

export default function MetricsDashboard({ service, refreshKey, onBack }) {
  const [hours, setHours] = useState(24);
  const [metrics, setMetrics] = useState(null);
  const [latency, setLatency] = useState(null);
  const [checks, setChecks] = useState([]);
  const [error, setError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.metrics(service.id, hours), api.latency(service.id, hours), api.checks(service.id, 200)])
      .then(([metricResult, latencyResult, checkResult]) => {
        if (cancelled) return;
        setMetrics(metricResult);
        setLatency(latencyResult);
        setChecks(checkResult);
        setError(null);
        setLastUpdated(Date.now());
      })
      .catch((e) => !cancelled && setError(e.message));
    return () => { cancelled = true; };
  }, [service.id, hours, refreshKey]);

  // Ticks once a second purely so the "updated Xs ago" label stays current;
  // does not touch data fetching, computation, or any value shown elsewhere.
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 1000);
    return () => clearInterval(id);
  }, []);

  const groups = useMemo(() => metricGroups(metrics?.points), [metrics]);
  const stats = latency?.stats;
  const byName = (name) => groups.find(([metricName]) => metricName.includes(name))?.[1] || [];
  const responsePoints = latency?.points || [];
  const cpuPoints = byName("cpu.usage");
  const memoryPoints = byName("memory.used");
  const requestSamples = byName("http.server.requests");
  const requestPoints = requestSamples.slice(1).map((point, index) => {
    const previous = requestSamples[index];
    const elapsedMinutes = Math.max(1 / 60, (new Date(point.sampledAt).getTime() - new Date(previous.sampledAt).getTime()) / 60000);
    return { ...point, value: Math.max(0, point.value - previous.value) / elapsedMinutes };
  });
  const errorPoints = checks.map((check) => ({ value: check.statusCode >= 400 || !check.success ? 100 : 0 }));
  const responseValues = checks.filter((check) => check.success && check.responseTimeMs != null).map((check) => check.responseTimeMs);

  return (
    <section className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <button type="button" className="btn-quiet mb-3" onClick={onBack}>← All services</button>
          <div className="flex flex-wrap items-center gap-3">
            <h2 className="text-2xl font-semibold tracking-tight">{service.name}</h2>
            <StatusMark service={service} />
          </div>
          <a href={service.url} target="_blank" rel="noreferrer noopener" className="text-sm text-signal hover:underline">{service.url}</a>
          <div className="mt-2">
            <LiveBadge lastUpdated={lastUpdated} tick={tick} ok={!error} />
          </div>
        </div>
        <div role="group" aria-label="Metrics time window" className="flex overflow-hidden rounded border border-rule">
          {WINDOWS.map((window) => (
            <button key={window.hours} type="button" aria-pressed={hours === window.hours} onClick={() => setHours(window.hours)} className={`px-3 py-1.5 text-sm transition-colors ${hours === window.hours ? "bg-ink text-white" : "bg-panel hover:bg-wash"}`}>
              {window.label}
            </button>
          ))}
        </div>
      </div>

      {error && <Notice tone="error">Could not load this service’s metrics: {error}</Notice>}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[
          ["Availability", stats?.availabilityPercent == null ? "—" : formatPercent(stats.availabilityPercent), `${stats?.successfulChecks || 0} successful checks`, stats?.availabilityPercent != null && stats.availabilityPercent < 99],
          ["Average response", stats?.avgResponseMs == null ? "—" : formatMs(stats.avgResponseMs), "Successful checks", false],
          ["95th percentile", stats?.p95ResponseMs == null ? "—" : formatMs(stats.p95ResponseMs), "Response time", false],
          ["Metric samples", metrics?.points?.length || 0, `Last ${hours} hours`, false],
        ].map(([label, value, note, warn]) => (
          <div key={label} className={`border-l-2 border-y border-r border-rule bg-panel px-4 py-4 ${warn ? "border-l-down" : "border-l-up"}`}>
            <p className="text-sm text-muted">{label}</p>
            <p className="mt-1 text-2xl font-semibold tracking-tight tabular-nums md:text-3xl">{value}</p>
            <p className="mt-1 text-xs text-muted">{note}</p>
          </div>
        ))}
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <Panel title="API Response Time" type="Line chart · response time (ms) over time">
          <LineChart points={responsePoints} value={(point) => point.ms || 0} label="API response time" live />
          <p className="text-sm text-muted">Latest <strong className="text-ink">{formatMs(responsePoints.at(-1)?.ms)}</strong> · p95 <strong className="text-ink">{formatMs(stats?.p95ResponseMs)}</strong></p>
        </Panel>
        <Panel title="CPU Usage" type="Area chart · CPU utilization (%) over time">
          <AreaChart points={cpuPoints} value={(point) => point.value * 100} label="CPU usage" />
          <p className="text-sm text-muted">Latest <strong className="text-ink">{cpuPoints.length ? displayMetricValue("cpu.usage", cpuPoints.at(-1).value) : "—"}</strong></p>
        </Panel>
        <Panel title="Memory Usage" type="Area chart · JVM memory usage (MB) over time">
          <AreaChart points={memoryPoints} value={(point) => point.value} color="#A77BFF" label="Memory usage" />
          <p className="text-sm text-muted">Latest <strong className="text-ink">{memoryPoints.length ? displayMetricValue("jvm.memory.used", memoryPoints.at(-1).value) : "—"}</strong></p>
        </Panel>
        <Panel title="Request Traffic" type="Line chart · requests per minute over time">
          <LineChart points={requestPoints} value={(point) => point.value} color="#F2B84B" label="Request traffic" />
          <p className="text-sm text-muted">Latest rate: <strong className="text-ink">{requestPoints.length ? `${Math.round(requestPoints.at(-1).value).toLocaleString()} req/min` : "—"}</strong></p>
        </Panel>
        <Panel title="HTTP Error Rate" type="Line chart · 4xx and 5xx error percentages over time">
          <LineChart points={errorPoints} value={(point) => point.value} color="#E86A6A" label="HTTP error rate" />
          <p className="text-sm text-muted">Failed health checks: <strong className="text-ink">{checks.length ? `${((checks.filter((check) => !check.success).length / checks.length) * 100).toFixed(1)}%` : "—"}</strong></p>
        </Panel>
        <Panel title="API Latency Distribution" type="Histogram · p50, p95, p99 response times">
          <Histogram checks={checks} />
          <p className="mt-3 text-sm text-muted">p50 <strong className="text-ink">{formatMs(percentile(responseValues, 0.5))}</strong> · p95 <strong className="text-ink">{formatMs(percentile(responseValues, 0.95))}</strong> · p99 <strong className="text-ink">{formatMs(percentile(responseValues, 0.99))}</strong></p>
        </Panel>
        <Panel title="Service Availability" type="Timeline · UP, DOWN and downtime periods">
          <div className="flex h-24 items-center gap-1 rounded bg-wash p-4">{checks.length ? checks.map((check) => <span key={check.id} className={`h-full flex-1 rounded-sm transition-colors ${check.success ? "bg-up" : "bg-down"}`} title={`${check.success ? "UP" : "DOWN"} · ${absoluteTime(check.checkedAt)}`} />) : <span className="text-sm text-muted">No check history yet.</span>}</div>
          <p className="mt-3 text-sm text-muted">Current status: <strong className="text-ink">{service.status}</strong> · {formatPercent(stats?.availabilityPercent)} uptime</p>
        </Panel>
        <Panel title="HTTP Status Code Distribution" type="Donut chart · 2xx, 3xx, 4xx and 5xx responses">
          <Donut checks={checks} />
        </Panel>
      </div>
    </section>
  );
}