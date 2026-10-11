import { useState } from "react";
import { api, ApiError } from "../lib/api.js";
import { Notice } from "./ui.jsx";

const EMPTY = {
  name: "",
  url: "",
  provider: "VERCEL",
  checkIntervalSeconds: 60,
  timeoutMs: 10000,
  failureThreshold: 3,
  enabled: true,
  actuatorMetricsEnabled: false,
};

function Field({ id, label, hint, error, children }) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-msg`} className="mt-1 text-xs text-down">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-msg`} className="mt-1 text-xs text-muted">
            {hint}
          </p>
        )
      )}
    </div>
  );
}

export default function ServiceForm({ service, onClose, onSaved }) {
  const editing = Boolean(service);
  const [form, setForm] = useState(
    service
      ? {
          name: service.name,
          url: service.url,
          provider: service.provider,
          checkIntervalSeconds: service.checkIntervalSeconds,
          timeoutMs: service.timeoutMs,
          failureThreshold: service.failureThreshold,
          enabled: service.enabled,
          actuatorMetricsEnabled: service.actuatorMetricsEnabled,
        }
      : EMPTY,
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});

  const set = (key) => (event) => {
    const value = event.target.type === "checkbox" ? event.target.checked : event.target.value;
    setForm((f) => ({ ...f, [key]: value }));
  };

  async function submit(event) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setFieldErrors({});
    const payload = {
      ...form,
      name: form.name.trim(),
      url: form.url.trim(),
      checkIntervalSeconds: Number(form.checkIntervalSeconds),
      timeoutMs: Number(form.timeoutMs),
      failureThreshold: Number(form.failureThreshold),
    };
    try {
      if (editing) await api.updateService(service.id, payload);
      else await api.createService(payload);
      onSaved();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not save the service.");
      if (e instanceof ApiError) setFieldErrors(e.fieldErrors);
      setSaving(false);
    }
  }

  async function remove() {
    if (!window.confirm(`Delete "${service.name}" and all of its check history? This cannot be undone.`)) return;
    setSaving(true);
    try {
      await api.deleteService(service.id);
      onSaved({ deleted: true });
    } catch (e) {
      setError(e.message);
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="p-6">
      <h2 id="form-title" className="text-lg font-semibold">
        {editing ? "Edit service" : "Add a service"}
      </h2>
      <p className="mt-1 text-sm text-muted">CloudPulse only checks public http and https addresses. Localhost and private network URLs are rejected.</p>

      {error && (
        <div className="mt-4">
          <Notice tone="error">{error}</Notice>
        </div>
      )}

      <div className="mt-5 grid gap-4">
        <Field id="svc-name" label="Name" error={fieldErrors.name}>
          <input id="svc-name" className="field" required maxLength={100} value={form.name} onChange={set("name")} autoFocus aria-describedby="svc-name-msg" />
        </Field>
        <Field id="svc-url" label="Health check URL" hint="For example https://my-app.vercel.app/ or a /health endpoint." error={fieldErrors.url}>
          <input id="svc-url" className="field" type="url" required maxLength={2048} placeholder="https://" value={form.url} onChange={set("url")} aria-describedby="svc-url-msg" />
        </Field>
        <Field id="svc-provider" label="Hosted on" error={fieldErrors.provider}>
          <select id="svc-provider" className="field" value={form.provider} onChange={set("provider")}>
            <option value="VERCEL">Vercel</option>
            <option value="RENDER">Render</option>
            <option value="AWS">AWS</option>
            <option value="OTHER">Other</option>
          </select>
        </Field>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Field id="svc-interval" label="Check every (seconds)" hint="10 to 3600" error={fieldErrors.checkIntervalSeconds}>
            <input id="svc-interval" className="field" type="number" min="10" max="3600" required value={form.checkIntervalSeconds} onChange={set("checkIntervalSeconds")} aria-describedby="svc-interval-msg" />
          </Field>
          <Field id="svc-timeout" label="Timeout (ms)" hint="1000 to 30000" error={fieldErrors.timeoutMs}>
            <input id="svc-timeout" className="field" type="number" min="1000" max="30000" step="500" required value={form.timeoutMs} onChange={set("timeoutMs")} aria-describedby="svc-timeout-msg" />
          </Field>
          <Field id="svc-threshold" label="Failures before alert" hint="1 to 10" error={fieldErrors.failureThreshold}>
            <input id="svc-threshold" className="field" type="number" min="1" max="10" required value={form.failureThreshold} onChange={set("failureThreshold")} aria-describedby="svc-threshold-msg" />
          </Field>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="h-4 w-4" checked={form.enabled} onChange={set("enabled")} />
          Monitor this service
        </label>
        <label className="flex items-start gap-2 text-sm">
          <input type="checkbox" className="mt-0.5 h-4 w-4" checked={form.actuatorMetricsEnabled} onChange={set("actuatorMetricsEnabled")} />
          <span>
            <span className="block">Collect Spring Boot Actuator metrics</span>
            <span className="text-xs text-muted">Reads public <code>/actuator/metrics</code> after successful checks.</span>
          </span>
        </label>
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          {editing && (
            <button type="button" className="btn-danger" onClick={remove} disabled={saving}>
              Delete service
            </button>
          )}
        </div>
        <div className="flex gap-2">
          <button type="button" className="btn-quiet" onClick={onClose} disabled={saving}>
            Cancel
          </button>
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving ? "Saving…" : editing ? "Save changes" : "Add service"}
          </button>
        </div>
      </div>
    </form>
  );
}
