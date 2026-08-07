"use client";

import { FormEvent, useEffect, useState } from "react";
import type { GuideSummary, GuideTemplate, GuideType } from "../../../lib/guide-types";

type CurrentUser = { tenantSlug: string; displayName: string };

async function request<T>(path: string, init?: RequestInit) {
  const response = await fetch(`/api/grace${path}`, { ...init, headers: { "content-type": "application/json", ...(init?.headers ?? {}) } });
  if (response.status === 401) window.location.assign("/login");
  if (!response.ok) throw new Error(await response.text());
  return response.json() as Promise<T>;
}

function slugify(value: string) {
  return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

export default function GuidesWorkspace() {
  const [tenant, setTenant] = useState("");
  const [guides, setGuides] = useState<GuideSummary[]>([]);
  const [types, setTypes] = useState<GuideType[]>([]);
  const [templates, setTemplates] = useState<GuideTemplate[]>([]);
  const [message, setMessage] = useState("Loading guides...");
  const [draft, setDraft] = useState({ title: "", slug: "", description: "", guideTypeKey: "SUBJECT", templateKey: "subject-basic" });

  async function load() {
    const meResponse = await fetch("/api/auth/me", { cache: "no-store" });
    if (!meResponse.ok) return window.location.assign("/login");
    const me = await meResponse.json() as CurrentUser;
    setTenant(me.tenantSlug);
    const [nextGuides, nextTypes, nextTemplates] = await Promise.all([
      request<GuideSummary[]>(`/tenants/${me.tenantSlug}/guides`),
      request<GuideType[]>(`/tenants/${me.tenantSlug}/guide-types`),
      request<GuideTemplate[]>(`/tenants/${me.tenantSlug}/guide-templates`)
    ]);
    setGuides(nextGuides);
    setTypes(nextTypes);
    setTemplates(nextTemplates);
    setMessage(`${nextGuides.length} guide${nextGuides.length === 1 ? "" : "s"}`);
  }

  useEffect(() => { void load().catch(error => setMessage(error instanceof Error ? error.message : "Could not load guides")); }, []);

  async function create(event: FormEvent) {
    event.preventDefault();
    setMessage("Creating guide...");
    const guide = await request<{ id: string }>(`/tenants/${tenant}/guides`, {
      method: "POST",
      body: JSON.stringify({ ...draft, tags: [] })
    });
    window.location.assign(`/admin/guides/${guide.id}`);
  }

  return (
    <main className="workspace-shell">
      <header className="workspace-header">
        <div><p className="eyebrow">Guide management</p><h1>Guides</h1></div>
        <nav className="admin-actions"><a className="nav-link" href="/admin/resources">Resources</a><a className="nav-link" href="/admin">Configuration</a><a className="nav-link" href="/">Public site</a></nav>
      </header>
      <p className="notice" role="status">{message}</p>
      <section className="guide-workspace-grid">
        <form className="panel create-guide-panel" onSubmit={create}>
          <div><p className="eyebrow">New guide</p><h2>Create from a template</h2></div>
          <label className="field"><span>Title</span><input required value={draft.title} onChange={event => setDraft({ ...draft, title: event.target.value, slug: slugify(event.target.value) })} /></label>
          <label className="field"><span>URL slug</span><input required pattern="[a-z0-9]+(?:-[a-z0-9]+)*" value={draft.slug} onChange={event => setDraft({ ...draft, slug: slugify(event.target.value) })} /></label>
          <label className="field"><span>Description</span><textarea rows={4} value={draft.description} onChange={event => setDraft({ ...draft, description: event.target.value })} /></label>
          <label className="field"><span>Guide type</span><select value={draft.guideTypeKey} onChange={event => setDraft({ ...draft, guideTypeKey: event.target.value, templateKey: "" })}>{types.map(type => <option key={type.id} value={type.key}>{type.displayName}</option>)}</select></label>
          <label className="field"><span>Template</span><select value={draft.templateKey} onChange={event => { const template = templates.find(item => item.key === event.target.value); setDraft({ ...draft, templateKey: event.target.value, guideTypeKey: template?.guideTypeKey ?? draft.guideTypeKey }); }}><option value="">Blank guide</option>{templates.map(template => <option key={template.id} value={template.key}>{template.name}</option>)}</select></label>
          <button type="submit">Create guide</button>
        </form>
        <section className="guide-list" aria-label="Editable guides">
          {guides.map(guide => (
            <article className="guide-row" key={guide.id}>
              <div><span className="status-label">{guide.status.replaceAll("_", " ")}</span><h2><a href={`/admin/guides/${guide.id}`}>{guide.title}</a></h2><p>{guide.description}</p></div>
              <dl><div><dt>Type</dt><dd>{guide.guideTypeName}</dd></div><div><dt>Owner</dt><dd>{guide.ownerName}</dd></div><div><dt>Revision</dt><dd>{guide.revisionNumber}</dd></div></dl>
            </article>
          ))}
          {!guides.length ? <div className="empty-state"><h2>No guides yet</h2><p>Create the first structured guide for this institution.</p></div> : null}
        </section>
      </section>
    </main>
  );
}
