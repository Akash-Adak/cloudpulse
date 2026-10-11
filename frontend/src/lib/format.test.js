import { describe, expect, it } from "vitest";
import { formatDuration, formatMs, formatPercent, relativeTime, statusLabel } from "./format.js";

describe("format helpers", () => {
  it("shows unknown values as a dash instead of inventing numbers", () => {
    expect(formatMs(null)).toBe("—");
    expect(formatPercent(undefined)).toBe("—");
    expect(formatDuration(null)).toBe("—");
  });

  it("formats latency", () => {
    expect(formatMs(120)).toBe("120 ms");
    expect(formatMs(1500)).toBe("1.50 s");
  });

  it("formats durations", () => {
    expect(formatDuration(45)).toBe("45s");
    expect(formatDuration(125)).toBe("2m 5s");
    expect(formatDuration(3 * 3600 + 120)).toBe("3h 2m");
  });

  it("formats relative time", () => {
    const now = Date.parse("2026-01-01T00:10:00Z");
    expect(relativeTime("2026-01-01T00:09:58Z", now)).toBe("just now");
    expect(relativeTime("2026-01-01T00:09:00Z", now)).toBe("1 min ago");
    expect(relativeTime(null, now)).toBe("never");
  });

  it("describes service state", () => {
    expect(statusLabel({ enabled: false, status: "UP" })).toBe("Paused");
    expect(statusLabel({ enabled: true, status: "DOWN" })).toBe("Down");
    expect(statusLabel({ enabled: true, status: "UNKNOWN" })).toBe("Not checked yet");
  });
});
