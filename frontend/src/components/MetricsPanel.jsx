import { useEffect, useMemo, useState } from "react";
import { api } from "../lib/api.js";
import { absoluteTime } from "../lib/format.js";
import { Notice } from "./ui.jsx";

export function displayMetricValue(name, value) {
  if (name.includes("cpu.usage")) return `${(value * 100).toFixed(1)}%`;
  if (name.includes("memory") || name.includes("disk.free")) return `${(value / 1024 / 1024).toFixed(1)} MB`;
  if (name.includes("requests") || name.includes("threads") || name.includes("connections")) return Math.round(value).toLocaleString();
  return value.toFixed(2);
}

export default function MetricsPanel({ service, refreshKey }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => {
    if (!service.actuatorMetricsEnabled) return;
    let cancelled = false;
    api.metrics(service.id, 24).then((result) => !cancelled && setData(result)).catch((e) => !cancelled && setError(e.message));
    return () => { cancelled = true; };
  }, [service.id, service.actuatorMetricsEnabled, refreshKey]);

  const latest = useMemo(() => {
    const map = new Map();
    for (const point of data?.points || []) map.set(point.name, point);
    return [...map.values()];
  }, [data]);

  if (!service.actuatorMetricsEnabled) return null;
  return (
    <section className="mt-8" aria-labelledby="metrics-title">
      <div className="flex items-center justify-between">
        <h3 id="metrics-title" className="text-base font-semibold">Application metrics</h3>
        <span className="text-xs text-muted">Spring Boot Actuator · last 24 hours</span>
      </div>
      {error && <div className="mt-3"><Notice tone="error">Could not load metrics: {error}</Notice></div>}
      {!error && latest.length === 0 && <p className="mt-3 text-sm text-muted">No metrics collected yet. The next successful check will fetch them.</p>}
      {latest.length > 0 && (
        <dl className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
          {latest.map((metric) => (
            <div key={metric.name} className="border border-rule bg-wash px-4 py-3">
              <dt className="truncate text-xs text-muted" title={metric.name}>{metric.name}</dt>
              <dd className="mt-1 text-xl font-semibold tabular-nums">{displayMetricValue(metric.name, metric.value)}</dd>
              <p className="mt-1 text-xs text-muted">Sampled {absoluteTime(metric.sampledAt)}</p>
            </div>
          ))}
        </dl>
      )}
    </section>
  );
}
