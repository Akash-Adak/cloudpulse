import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../lib/api.js";
import { absoluteTime, clockTime } from "../lib/format.js";
import IncidentList from "./IncidentList.jsx";
import MetricsDashboard from "./MetricsDashboard.jsx";
import ServiceDrawer from "./ServiceDrawer.jsx";
import ServiceForm from "./ServiceForm.jsx";
import ServiceList from "./ServiceList.jsx";
import SummaryBand from "./SummaryBand.jsx";
import { Modal, Notice, PulseMark } from "./ui.jsx";

const POLL_MS = 10000;

function useOnline() {
  const [online, setOnline] = useState(typeof navigator === "undefined" ? true : navigator.onLine);
  useEffect(() => {
    const up = () => setOnline(true);
    const down = () => setOnline(false);
    window.addEventListener("online", up);
    window.addEventListener("offline", down);
    return () => {
      window.removeEventListener("online", up);
      window.removeEventListener("offline", down);
    };
  }, []);
  return online;
}

export default function Dashboard({ username, onSignOut }) {
  const [services, setServices] = useState(null);
  const [summary, setSummary] = useState(null);
  const [incidents, setIncidents] = useState(null);
  const [updatedAt, setUpdatedAt] = useState(null);
  const [error, setError] = useState(null);
  const [refreshing, setRefreshing] = useState(false);
  const [now, setNow] = useState(Date.now());
  const [selectedId, setSelectedId] = useState(null);
  const [form, setForm] = useState(null); // { service?: Service } | null
  const [busyId, setBusyId] = useState(null);
  const [metricsService, setMetricsService] = useState(null);
  const online = useOnline();
  const inFlight = useRef(false);

  const load = useCallback(async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    setRefreshing(true);
    try {
      const [s, sum, inc] = await Promise.all([api.services(), api.summary(), api.incidents(30)]);
      setServices(s);
      setSummary(sum);
      setIncidents(inc);
      setUpdatedAt(new Date());
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      inFlight.current = false;
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load();
    const poll = setInterval(load, POLL_MS);
    const tick = setInterval(() => setNow(Date.now()), 10000);
    const onVisible = () => document.visibilityState === "visible" && load();
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("online", load);
    return () => {
      clearInterval(poll);
      clearInterval(tick);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("online", load);
    };
  }, [load]);

  async function toggle(service) {
    setBusyId(service.id);
    try {
      await api.setEnabled(service.id, !service.enabled);
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  }

  async function remove(service) {
    if (!window.confirm(`Delete "${service.name}" and all of its history? This cannot be undone.`)) return;
    setBusyId(service.id);
    try {
      await api.deleteService(service.id);
      if (metricsService?.id === service.id) setMetricsService(null);
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  }

  const selected = services?.find((s) => s.id === selectedId) || null;
  const firstLoad = services === null && !error;

  return (
    <div className="min-h-screen">
      <header className="border-b border-rule bg-panel">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-5 py-3">
          <div className="flex items-center gap-3">
            <PulseMark />
            <div>
              <h1 className="text-lg font-semibold leading-tight">CloudPulse</h1>
              <p className="text-xs text-muted">Unified cloud operations</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <p className="text-sm text-muted" title={updatedAt ? absoluteTime(updatedAt) : ""} aria-live="polite">
              {updatedAt ? `Live · updated ${clockTime(updatedAt)}` : "Connecting live…"}
            </p>
            <button type="button" className="btn-quiet" onClick={load} disabled={refreshing}>
              {refreshing ? "Refreshing…" : "Refresh"}
            </button>
            <button type="button" className="btn-primary" onClick={() => setForm({})}>
              Add service
            </button>
            <button type="button" className="btn-quiet" onClick={onSignOut} title={`Signed in as ${username}`}>
              Sign out
            </button>
          </div>
        </div>
      </header>

      <div className="mx-auto max-w-7xl space-y-4 px-5 py-6">
        {!online && (
          <Notice tone="warn">You are offline. {updatedAt ? `Showing the data received at ${clockTime(updatedAt)}.` : "Waiting for a connection."}</Notice>
        )}
        {error && online && (
          <Notice
            tone="error"
            action={
              <button type="button" className="btn-quiet" onClick={load}>
                Try again
              </button>
            }
          >
            {error}
            {updatedAt ? ` Showing the data received at ${clockTime(updatedAt)}.` : ""}
          </Notice>
        )}

        {firstLoad && <p className="py-16 text-center text-muted">Loading services…</p>}

        {services && metricsService && (
          <MetricsDashboard service={metricsService} refreshKey={updatedAt?.getTime()} onBack={() => setMetricsService(null)} />
        )}
        {services && !metricsService && (
          <>
            <SummaryBand summary={summary} />
            <section aria-labelledby="services-title" className="border border-rule">
              <div className="flex items-center justify-between border-b border-rule bg-panel px-5 py-3">
                <div><h2 id="services-title" className="text-base font-semibold">Services</h2><p className="text-xs text-muted">Manage, inspect, and monitor every service from here.</p></div>
                <button type="button" className="btn-primary" onClick={() => setForm({})}>Add service</button>
              </div>
              <ServiceList services={services} now={now} busyId={busyId} onOpen={setSelectedId} onEdit={(s) => setForm({ service: s })} onToggle={toggle} onDelete={remove} onMetrics={setMetricsService} onAdd={() => setForm({})} />
            </section>
            <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
              <section aria-labelledby="incidents-title" className="self-start border border-rule bg-panel">
                <h2 id="incidents-title" className="border-b border-rule px-5 py-3 text-base font-semibold">
                  Incident timeline
                </h2>
                <IncidentList incidents={incidents} onOpenService={setSelectedId} emptyText="No incidents yet. They appear here when a service fails its checks repeatedly." />
              </section>
            </div>
          </>
        )}
      </div>

      <Modal open={Boolean(selected)} onClose={() => setSelectedId(null)} labelledBy="drawer-title" variant="drawer">
        {selected && (
          <ServiceDrawer
            service={selected}
            now={now}
            refreshKey={updatedAt?.getTime()}
            onClose={() => setSelectedId(null)}
            onEdit={(s) => setForm({ service: s })}
            onToggle={toggle}
            onChanged={load}
          />
        )}
      </Modal>

      <Modal open={form !== null} onClose={() => setForm(null)} labelledBy="form-title">
        {form !== null && (
          <ServiceForm
            service={form.service}
            onClose={() => setForm(null)}
            onSaved={(result) => {
              setForm(null);
              if (result?.deleted) setSelectedId(null);
              load();
            }}
          />
        )}
      </Modal>
    </div>
  );
}
