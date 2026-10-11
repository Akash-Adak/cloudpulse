const SLOTS = 40;
const WIDTH = 200;
const HEIGHT = 32;
const MIN_BAR = 5;

/**
 * The last checks of a service, oldest to newest. Bar height is that check's response time
 * (relative to the slowest in the strip); a failed check is a full-height red bar.
 */
export default function HeartbeatStrip({ recent }) {
  if (!recent || recent.length === 0) {
    return <p className="text-sm text-muted">No checks yet</p>;
  }
  const times = recent.filter((r) => r.success && r.responseTimeMs != null).map((r) => r.responseTimeMs);
  const peak = Math.max(1, ...times);
  const slot = WIDTH / SLOTS;
  const barWidth = slot - 1.5;
  const offset = SLOTS - recent.length;
  const failed = recent.filter((r) => !r.success).length;

  return (
    <svg
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      className="h-8 w-full max-w-[220px]"
      role="img"
      aria-label={`Last ${recent.length} checks: ${recent.length - failed} succeeded, ${failed} failed`}
    >
      <line x1="0" y1={HEIGHT - 0.5} x2={WIDTH} y2={HEIGHT - 0.5} className="stroke-rule" strokeWidth="1" />
      {recent.map((r, i) => {
        const h = r.success
          ? MIN_BAR + ((r.responseTimeMs ?? 0) / peak) * (HEIGHT - MIN_BAR - 2)
          : HEIGHT - 2;
        return (
          <rect
            key={i}
            x={(offset + i) * slot}
            y={HEIGHT - 1 - h}
            width={barWidth}
            height={h}
            rx="1"
            className={r.success ? "fill-up" : "fill-down"}
          >
            <title>{r.success ? `${r.responseTimeMs} ms` : "Failed"}</title>
          </rect>
        );
      })}
    </svg>
  );
}
