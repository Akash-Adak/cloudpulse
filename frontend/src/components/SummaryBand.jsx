import { formatMs } from "../lib/format.js";

function Figure({ label, value, note, tone }) {
  const color = tone === "down" ? "text-down" : tone === "up" ? "text-up" : "text-ink";
  return (
    <div className="px-5 py-4">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className={`mt-1 text-3xl font-semibold tabular-nums ${color}`}>{value}</dd>
      <p className="mt-1 min-h-[1.25rem] text-xs text-muted">{note}</p>
    </div>
  );
}

export default function SummaryBand({ summary }) {
  if (!summary) return null;
  const avg = summary.avgResponseMs1h;
  return (
    <dl className="grid grid-cols-2 divide-rule border-y border-rule bg-panel sm:grid-cols-3 lg:grid-cols-5 lg:divide-x">
      <Figure label="Services" value={summary.totalServices} note={summary.disabledServices ? `${summary.disabledServices} paused` : "All being monitored"} />
      <Figure label="Up" value={summary.up} tone={summary.up > 0 ? "up" : undefined} note={summary.unknown ? `${summary.unknown} not checked yet` : ""} />
      <Figure label="Down" value={summary.down} tone={summary.down > 0 ? "down" : undefined} />
      <Figure label="Average response" value={avg == null ? "No data" : formatMs(avg)} note="Successful checks, last hour" />
      <Figure label="Active incidents" value={summary.activeIncidents} tone={summary.activeIncidents > 0 ? "down" : undefined} />
    </dl>
  );
}
