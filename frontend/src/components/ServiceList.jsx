import HeartbeatStrip from "./HeartbeatStrip.jsx";
import { StatusMark } from "./ui.jsx";
import { absoluteTime, formatMs, relativeTime } from "../lib/format.js";

const PROVIDERS = { VERCEL: "Vercel", RENDER: "Render", AWS: "AWS", OTHER: "Other" };

export default function ServiceList({ services, now, onOpen, onEdit, onToggle, onDelete, onMetrics, onAdd, busyId }) {
  if (services.length === 0) {
    return (
      <div className="bg-panel px-6 py-12 text-center">
        <h3 className="text-lg font-semibold">No services yet</h3>
        <p className="mx-auto mt-2 max-w-md text-sm text-muted">
          Add the first Vercel, Render or AWS app you want to watch. CloudPulse will start checking it within a few seconds.
        </p>
        <button type="button" className="btn-primary mt-5" onClick={onAdd}>
          Add a service
        </button>
      </div>
    );
  }

  return (
    <ul className="divide-y divide-rule bg-panel">
      {services.map((s) => (
        <li key={s.id} className="grid gap-4 border-l-4 border-transparent px-5 py-4 transition-colors hover:border-signal hover:bg-wash sm:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_auto] sm:items-center">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <button type="button" onClick={() => onOpen(s.id)} className="text-left text-base font-semibold hover:text-signal hover:underline">
                {s.name}
              </button>
              <StatusMark service={s} />
            </div>
            <p className="mt-0.5 truncate text-sm text-muted" title={s.url}>
              {s.url}
            </p>
            <p className="mt-0.5 text-xs text-muted">{PROVIDERS[s.provider] || s.provider}</p>
          </div>

          <div>
            <HeartbeatStrip recent={s.recent} />
            <p className="mt-1 text-xs text-muted" title={s.lastCheckedAt ? absoluteTime(s.lastCheckedAt) : "No checks yet"}>
              {s.lastCheckedAt
                ? `${formatMs(s.lastResponseTimeMs)}${s.lastStatusCode ? `, HTTP ${s.lastStatusCode}` : ""}, checked ${relativeTime(s.lastCheckedAt, now)}`
                : s.enabled
                  ? "Waiting for the first check"
                  : "Monitoring is paused"}
            </p>
          </div>

          <div className="flex flex-wrap justify-end gap-2">
            {s.actuatorMetricsEnabled && (
              <button type="button" className="btn-primary" onClick={() => onMetrics(s)}>
                Metrics
              </button>
            )}
            <button type="button" className="btn-quiet" disabled={busyId === s.id} onClick={() => onToggle(s)}>
              {s.enabled ? "Pause" : "Resume"}
            </button>
            <button type="button" className="btn-quiet" onClick={() => onEdit(s)}>
              Edit
            </button>
            <button type="button" className="btn-danger" onClick={() => onDelete(s)}>
              Delete
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}
