import { useEffect, useState } from "react";
import { api } from "../lib/api.js";
import { absoluteTime, formatMs, formatPercent, relativeTime } from "../lib/format.js";
import IncidentList from "./IncidentList.jsx";
import LatencyChart from "./LatencyChart.jsx";
import MetricsPanel from "./MetricsPanel.jsx";
import { Notice, StatusMark } from "./ui.jsx";

const WINDOWS = [
  { hours: 1, label: "1 hour" },
  { hours: 6, label: "6 hours" },
  { hours: 24, label: "24 hours" },
  { hours: 168, label: "7 days" },
];

function Stat({ label, value, note }) {
  return (
    <div>
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="text-xl font-semibold tabular-nums">{value}</dd>
      {note && <p className="text-xs text-muted">{note}</p>}
    </div>
  );
}

export default function ServiceDrawer({ service, now, refreshKey, onClose, onEdit, onToggle, onChanged }) {
  const [hours, setHours] = useState(24);
  const [latency, setLatency] = useState(null);
  const [checks, setChecks] = useState(null);
  const [incidents, setIncidents] = useState(null);
  const [error, setError] = useState(null);
  const [checking, setChecking] = useState(false);
  const [checkResult, setCheckResult] = useState(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.latency(service.id, hours), api.checks(service.id, 25), api.serviceIncidents(service.id)])
      .then(([l, c, i]) => {
        if (cancelled) return;
        setLatency(l);
        setChecks(c);
        setIncidents(i);
        setError(null);
      })
      .catch((e) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [service.id, hours, refreshKey]);

  async function checkNow() {
    setChecking(true);
    setCheckResult(null);
    try {
      const result = await api.checkNow(service.id);
      setCheckResult(result);
      onChanged();
    } catch (e) {
      setCheckResult({ success: false, error: e.message });
    } finally {
      setChecking(false);
    }
  }

  const stats = latency?.stats;

  return (
    <div className="flex h-full flex-col">
      <header className="border-b border-rule px-6 py-5">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h2 id="drawer-title" className="text-xl font-semibold">
              {service.name}
            </h2>
            <a href={service.url} target="_blank" rel="noreferrer noopener" className="block truncate text-sm text-signal hover:underline">
              {service.url}
            </a>
            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1">
              <StatusMark service={service} />
              <span className="text-sm text-muted" title={absoluteTime(service.lastCheckedAt)}>
                {service.lastCheckedAt ? `Last checked ${relativeTime(service.lastCheckedAt, now)}` : "Not checked yet"}
              </span>
            </div>
          </div>
          <button type="button" className="btn-quiet shrink-0" onClick={onClose}>
            Close
          </button>
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          <button type="button" className="btn-primary" onClick={checkNow} disabled={checking}>
            {checking ? "Checking…" : "Check now"}
          </button>
          <button type="button" className="btn-quiet" onClick={() => onToggle(service)}>
            {service.enabled ? "Pause monitoring" : "Resume monitoring"}
          </button>
          <button type="button" className="btn-quiet" onClick={() => onEdit(service)}>
            Edit
          </button>
        </div>
        {checkResult && (
          <div className="mt-3" aria-live="polite">
            <Notice tone={checkResult.success ? "info" : "error"}>
              {checkResult.success
                ? `Check passed: HTTP ${checkResult.statusCode} in ${formatMs(checkResult.responseTimeMs)}.`
                : `Check failed: ${checkResult.error}`}
            </Notice>
          </div>
        )}
      </header>

      <div className="flex-1 overflow-y-auto px-6 py-5">
        {error && <Notice tone="error">Could not load history: {error}</Notice>}

        <section aria-labelledby="latency-title">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h3 id="latency-title" className="text-base font-semibold">
              Response time
            </h3>
            <div role="group" aria-label="Time window" className="flex overflow-hidden rounded border border-rule">
              {WINDOWS.map((w) => (
                <button
                  key={w.hours}
                  type="button"
                  aria-pressed={hours === w.hours}
                  onClick={() => setHours(w.hours)}
                  className={`px-3 py-1 text-sm ${hours === w.hours ? "bg-ink text-white" : "bg-panel hover:bg-wash"}`}
                >
                  {w.label}
                </button>
              ))}
            </div>
          </div>
          <div className="mt-3">{latency ? <LatencyChart points={latency.points} since={latency.since} hours={latency.hours} /> : <p className="py-8 text-sm text-muted">Loading history…</p>}</div>
          {stats && (
            <dl className="mt-4 grid grid-cols-2 gap-4 border-t border-rule pt-4 sm:grid-cols-4">
              <Stat label="Availability" value={stats.availabilityPercent == null ? "No data" : formatPercent(stats.availabilityPercent)} note={`${stats.successfulChecks} of ${stats.totalChecks} checks passed`} />
              <Stat label="Average" value={stats.avgResponseMs == null ? "No data" : formatMs(stats.avgResponseMs)} />
              <Stat label="95th percentile" value={stats.p95ResponseMs == null ? "No data" : formatMs(stats.p95ResponseMs)} />
              <Stat label="Slowest" value={stats.maxResponseMs == null ? "No data" : formatMs(stats.maxResponseMs)} />
            </dl>
          )}
        </section>

        <MetricsPanel service={service} refreshKey={refreshKey} />

        <section className="mt-8" aria-labelledby="incidents-title">
          <h3 id="incidents-title" className="text-base font-semibold">
            Incidents
          </h3>
          <div className="-mx-5">
            <IncidentList incidents={incidents} showService={false} emptyText="No incidents for this service." />
          </div>
        </section>

        <section className="mt-8" aria-labelledby="checks-title">
          <h3 id="checks-title" className="text-base font-semibold">
            Recent checks
          </h3>
          {checks && checks.length === 0 && <p className="mt-2 text-sm text-muted">No checks have run yet.</p>}
          {checks && checks.length > 0 && (
            <div className="mt-2 overflow-x-auto">
              <table className="w-full min-w-[480px] text-left text-sm">
                <thead className="border-b border-rule text-muted">
                  <tr>
                    <th className="py-2 pr-3 font-medium">Time</th>
                    <th className="py-2 pr-3 font-medium">Result</th>
                    <th className="py-2 pr-3 font-medium">Response</th>
                    <th className="py-2 font-medium">Detail</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-rule">
                  {checks.map((c) => (
                    <tr key={c.id}>
                      <td className="py-2 pr-3 whitespace-nowrap">{absoluteTime(c.checkedAt)}</td>
                      <td className={`py-2 pr-3 font-medium ${c.success ? "text-up" : "text-down"}`}>{c.success ? `Passed${c.statusCode ? ` (${c.statusCode})` : ""}` : "Failed"}</td>
                      <td className="py-2 pr-3 tabular-nums">{formatMs(c.responseTimeMs)}</td>
                      <td className="py-2 text-muted">{c.errorMessage || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <p className="mt-8 text-xs text-muted">
          Checked every {service.checkIntervalSeconds}s with a {service.timeoutMs / 1000}s timeout. An incident opens after {service.failureThreshold} failed checks in a row.
        </p>
      </div>
    </div>
  );
}
