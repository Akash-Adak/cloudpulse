import { useEffect, useMemo, useState } from "react";
import { api } from "../lib/api.js";
import { absoluteTime } from "../lib/format.js";
import { displayMetricValue } from "./MetricsPanel.jsx";
import MetricTrend from "./MetricTrend.jsx";
import { Notice } from "./ui.jsx";

function LatestMetrics({ result }) {
  const latest = useMemo(() => {
    const map = new Map();
    for (const point of result?.points || []) map.set(point.name, point);
    return [...map.values()];
  }, [result]);
  const series = useMemo(() => {
    const grouped = new Map();
    for (const point of result?.points || []) {
      if (!grouped.has(point.name)) grouped.set(point.name, []);
      grouped.get(point.name).push(point);
    }
    return [...grouped.entries()];
  }, [result]);

  if (latest.length === 0) {
    return <p className="mt-2 text-sm text-muted">No metrics collected yet.</p>;
  }

  return (
    <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
      {latest.map((metric) => (
        <div key={metric.name} className="border border-rule bg-wash px-4 py-3">
          <dt className="truncate text-xs text-muted" title={metric.name}>{metric.name}</dt>
          <dd className="mt-1 text-xl font-semibold tabular-nums">{displayMetricValue(metric.name, metric.value)}</dd>
          <p className="mt-1 text-xs text-muted">Sampled {absoluteTime(metric.sampledAt)}</p>
          <MetricTrend name={metric.name} points={series.find(([name]) => name === metric.name)?.[1] || []} />
        </div>
      ))}
    </div>
  );
}

export default function MetricsOverview({ services, refreshKey }) {
  const metricServices = services.filter((service) => service.actuatorMetricsEnabled);
  const [results, setResults] = useState({});
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    if (metricServices.length === 0) {
      setResults({});
      setError(null);
      return undefined;
    }

    Promise.all(metricServices.map(async (service) => [service.id, await api.metrics(service.id, 24)]))
      .then((entries) => {
        if (cancelled) return;
        setResults(Object.fromEntries(entries));
        setError(null);
      })
      .catch((e) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [services, refreshKey]);

  if (metricServices.length === 0) return null;

  return (
    <section aria-labelledby="metrics-overview-title" className="border border-rule bg-panel">
      <div className="border-b border-rule px-5 py-3">
        <h2 id="metrics-overview-title" className="text-base font-semibold">Application metrics</h2>
        <p className="mt-1 text-xs text-muted">Spring Boot Actuator · live latest samples and trends from the last 24 hours</p>
      </div>
      <div className="space-y-6 px-5 py-5">
        {error && <Notice tone="error">Could not load application metrics: {error}</Notice>}
        {metricServices.map((service) => (
          <section key={service.id} aria-labelledby={`metrics-service-${service.id}`}>
            <h3 id={`metrics-service-${service.id}`} className="font-medium">{service.name}</h3>
            {results[service.id] ? <LatestMetrics result={results[service.id]} /> : !error && <p className="mt-2 text-sm text-muted">Loading metrics…</p>}
          </section>
        ))}
      </div>
    </section>
  );
}
