import { absoluteTime, formatDuration } from "../lib/format.js";

export default function IncidentList({ incidents, onOpenService, showService = true, emptyText }) {
  if (!incidents || incidents.length === 0) {
    return <p className="px-5 py-6 text-sm text-muted">{emptyText || "No incidents recorded."}</p>;
  }
  return (
    <ol className="relative px-5 py-4">
      <span className="absolute bottom-6 left-[27px] top-6 w-px bg-rule" aria-hidden="true" />
      {incidents.map((i) => (
        <li key={i.id} className="relative pb-6 pl-8 last:pb-0">
          <span
            className={`absolute left-0 top-1.5 h-3 w-3 rounded-full border-2 border-panel ring-2 ${i.active ? "bg-down ring-down" : "bg-up ring-up"}`}
            aria-hidden="true"
          />
          <p className="text-sm font-semibold">
            {showService && onOpenService ? (
              <button type="button" className="hover:text-signal hover:underline" onClick={() => onOpenService(i.serviceId)}>
                {i.serviceName}
              </button>
            ) : (
              showService && i.serviceName
            )}
            {showService ? " " : ""}
            <span className={i.active ? "text-down" : "text-up"}>
              {showService ? (i.active ? "is down" : "recovered") : i.active ? "Open incident" : "Resolved incident"}
            </span>
          </p>
          <p className="mt-1 text-sm text-muted">Detected {absoluteTime(i.startedAt)}</p>
          {i.reason && <p className="text-sm">Reason: {i.reason}</p>}
          <p className="text-sm text-muted">
            {i.active
              ? `Still down after ${formatDuration(i.durationSeconds)}`
              : `Recovered ${absoluteTime(i.resolvedAt)} after ${formatDuration(i.durationSeconds)}`}
          </p>
        </li>
      ))}
    </ol>
  );
}
