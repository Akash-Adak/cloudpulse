import { useEffect, useRef } from "react";
import { statusLabel } from "../lib/format.js";

export function PulseMark({ size = 28 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="6" fill="#14202B" />
      <path d="M3 17h7l3-9 5 17 3-8h8" fill="none" stroke="#3FD08A" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

const TONES = {
  up: "text-up",
  down: "text-down",
  idle: "text-idle",
  paused: "text-muted",
};

function toneOf(service) {
  if (!service.enabled) return "paused";
  if (service.status === "UP") return "up";
  if (service.status === "DOWN") return "down";
  return "idle";
}

const DOT = { up: "bg-up", down: "bg-down", idle: "bg-idle", paused: "bg-muted" };

export function StatusMark({ service }) {
  const tone = toneOf(service);
  return (
    <span className={`inline-flex items-center gap-2 text-sm font-medium ${TONES[tone]}`}>
      <span className={`h-2.5 w-2.5 rounded-full ${DOT[tone]}`} aria-hidden="true" />
      {statusLabel(service)}
    </span>
  );
}

export function Notice({ tone = "info", children, action }) {
  const styles = {
    info: "border-signal bg-wash text-ink",
    warn: "border-idle bg-[#FBF3E1] text-ink",
    error: "border-down bg-[#FBEAE8] text-ink",
  };
  return (
    <div role={tone === "error" ? "alert" : "status"} className={`flex flex-wrap items-center justify-between gap-3 border-l-4 px-4 py-3 text-sm ${styles[tone]}`}>
      <div>{children}</div>
      {action}
    </div>
  );
}

function ModalInner({ onClose, labelledBy, className, children }) {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby={labelledBy}
      className={className}
      onClose={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      {children}
    </dialog>
  );
}

/** Native <dialog>: focus is trapped, Esc closes it, and background content is inert. */
export function Modal({ open, onClose, labelledBy, variant = "center", children }) {
  if (!open) return null;
  const className =
    variant === "drawer"
      ? "fixed inset-y-0 right-0 left-auto m-0 h-screen max-h-none w-full max-w-2xl bg-panel p-0 text-ink shadow-2xl"
      : "m-auto w-full max-w-lg rounded bg-panel p-0 text-ink shadow-2xl";
  return (
    <ModalInner onClose={onClose} labelledBy={labelledBy} className={className}>
      {children}
    </ModalInner>
  );
}
