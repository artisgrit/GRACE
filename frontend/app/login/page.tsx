"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

export default function LoginPage() {
  const router = useRouter();
  const [tenantSlug, setTenantSlug] = useState("default");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setMessage("");
    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ tenantSlug, email, password })
    });
    setSubmitting(false);
    if (!response.ok) {
      setMessage("Sign-in failed. Check the institution, email, and password.");
      return;
    }
    router.replace("/admin");
    router.refresh();
  }

  return (
    <main className="login-shell">
      <form className="login-panel" onSubmit={submit}>
        <div>
          <p className="eyebrow">GRACE administration</p>
          <h1>Sign in</h1>
          <p>Use your institution account to manage library services.</p>
        </div>
        <label className="field">
          <span>Institution</span>
          <input value={tenantSlug} onChange={(event) => setTenantSlug(event.target.value)} autoComplete="organization" required />
        </label>
        <label className="field">
          <span>Email address</span>
          <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" required />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required />
        </label>
        {message ? <p className="form-error" role="alert">{message}</p> : null}
        <button type="submit" disabled={submitting}>{submitting ? "Signing in..." : "Sign in"}</button>
        <a className="quiet-link" href="/">Return to public site</a>
      </form>
    </main>
  );
}
