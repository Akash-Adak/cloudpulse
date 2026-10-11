import { useState } from "react";
import { api, setToken } from "../lib/api.js";
import { Notice, PulseMark } from "./ui.jsx";

export default function Login({ onSignedIn, notice }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const result = await api.login(username.trim(), password);
      setToken(result.token);
      onSignedIn(result.username);
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6 py-12">
      <div className="flex items-center gap-3">
        <PulseMark size={36} />
        <h1 className="text-2xl font-semibold">CloudPulse</h1>
      </div>
      <p className="mt-2 text-muted">Sign in to see how your deployed apps are doing.</p>

      <form onSubmit={submit} className="mt-8 space-y-4 border border-rule bg-panel p-6">
        {(error || notice) && <Notice tone={error ? "error" : "info"}>{error || notice}</Notice>}
        <div>
          <label htmlFor="username" className="mb-1 block text-sm font-medium">
            Username
          </label>
          <input id="username" className="field" autoComplete="username" required value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </div>
        <div>
          <label htmlFor="password" className="mb-1 block text-sm font-medium">
            Password
          </label>
          <input id="password" className="field" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <button type="submit" className="btn-primary w-full" disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </main>
  );
}
