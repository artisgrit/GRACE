"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import type { GuideSummary } from "../../../lib/guide-types";
import type { LibraryResource, Subject } from "../../../lib/resource-types";

type CurrentUser = { tenantSlug: string };
type ResourceType = "DATABASE" | "LIBRARIAN" | "FAQ" | "BOOK" | "READING_LIST";
const TYPES: ResourceType[] = ["DATABASE", "LIBRARIAN", "FAQ", "BOOK", "READING_LIST"];

async function request<T>(path: string, init?: RequestInit) {
  const response = await fetch(`/api/grace${path}`, { ...init, headers: { "content-type": "application/json", ...(init?.headers ?? {}) } });
  if (response.status === 401) window.location.assign("/login");
  if (!response.ok) throw new Error(await response.text());
  return response.json() as Promise<T>;
}
function slugify(value: string) { return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, ""); }
function blankResource(type: ResourceType): Omit<LibraryResource, "id" | "updatedAt"> {
  return { resourceType: type, title: "", slug: "", description: "", status: "DRAFT", proxyMode: "NONE", authors: [], featured: false, openAccess: false, helpfulVotes: 0, unhelpfulVotes: 0, metadata: {}, subjectIds: [], guideIds: [], items: [], version: 0 };
}

export default function ResourceWorkspace() {
  const [tenant, setTenant] = useState("");
  const [type, setType] = useState<ResourceType>("DATABASE");
  const [resources, setResources] = useState<LibraryResource[]>([]);
  const [books, setBooks] = useState<LibraryResource[]>([]);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [guides, setGuides] = useState<GuideSummary[]>([]);
  const [draft, setDraft] = useState<Omit<LibraryResource, "id" | "updatedAt"> & { id?: string }>(blankResource("DATABASE"));
  const [subjectDraft, setSubjectDraft] = useState({ id: "", label: "", slug: "", alternativeLabels: [] as string[], description: "", notation: "", broaderSubjectId: "", relatedSubjectIds: [] as string[], guideIds: [] as string[], active: true });
  const [subjectMode, setSubjectMode] = useState(false);
  const [query, setQuery] = useState("");
  const [message, setMessage] = useState("Loading library resources...");

  async function load(selectedType = type) {
    const meResponse = await fetch("/api/auth/me", { cache: "no-store" });
    if (!meResponse.ok) return window.location.assign("/login");
    const me = await meResponse.json() as CurrentUser;
    setTenant(me.tenantSlug);
    const [nextResources, nextSubjects, nextGuides, nextBooks] = await Promise.all([
      request<LibraryResource[]>(`/tenants/${me.tenantSlug}/resources?type=${selectedType}`),
      request<Subject[]>(`/tenants/${me.tenantSlug}/subjects`),
      request<GuideSummary[]>(`/tenants/${me.tenantSlug}/guides`),
      request<LibraryResource[]>(`/tenants/${me.tenantSlug}/resources?type=BOOK`)
    ]);
    setResources(nextResources); setSubjects(nextSubjects); setGuides(nextGuides); setBooks(nextBooks);
    setMessage(`${nextResources.length} ${selectedType.toLowerCase().replaceAll("_", " ")}${nextResources.length === 1 ? "" : "s"}`);
  }
  useEffect(() => { void load().catch(error => setMessage(error instanceof Error ? error.message : "Could not load resources")); }, []);

  function switchType(next: ResourceType) {
    setType(next); setSubjectMode(false); setDraft(blankResource(next)); setQuery("");
    void load(next).catch(error => setMessage(error instanceof Error ? error.message : "Could not load resources"));
  }

  async function saveResource(event: FormEvent) {
    event.preventDefault(); setMessage("Saving resource...");
    const payload = { ...draft, resourceType: type, alternativeTitle: draft.alternativeTitle || null, provider: draft.provider || null, publisher: draft.publisher || null, canonicalUrl: draft.canonicalUrl || null, accessType: draft.accessType || null, isbn: draft.isbn || null, email: draft.email || null, phone: draft.phone || null, orcid: draft.orcid || null, faqAnswer: draft.faqAnswer || null, trialEndsAt: draft.trialEndsAt || null };
    const saved = await request<LibraryResource>(`/tenants/${tenant}/resources${draft.id ? `/${draft.id}` : ""}`, { method: draft.id ? "PUT" : "POST", body: JSON.stringify(payload) });
    setDraft({ ...saved }); await load(type); setMessage("Resource saved");
  }

  async function saveSubject(event: FormEvent) {
    event.preventDefault();
    const payload = { ...subjectDraft, broaderSubjectId: subjectDraft.broaderSubjectId || null };
    await request<Subject>(`/tenants/${tenant}/subjects${subjectDraft.id ? `/${subjectDraft.id}` : ""}`, { method: subjectDraft.id ? "PUT" : "POST", body: JSON.stringify(payload) });
    setSubjectDraft({ id: "", label: "", slug: "", alternativeLabels: [], description: "", notation: "", broaderSubjectId: "", relatedSubjectIds: [], guideIds: [], active: true });
    await load(type); setMessage("Subject saved");
  }

  const visible = useMemo(() => resources.filter(item => `${item.title} ${item.description}`.toLowerCase().includes(query.toLowerCase())), [resources, query]);
  return <main className="workspace-shell resource-workspace">
    <header className="workspace-header"><div><p className="eyebrow">Library resources</p><h1>Resource centre</h1></div><nav className="admin-actions"><a className="nav-link" href="/admin/guides">Guides</a><a className="nav-link" href="/admin">Configuration</a><a className="nav-link" href="/resources">Public directory</a></nav></header>
    <p className="notice" role="status">{message}</p>
    <div className="resource-type-bar" role="tablist" aria-label="Resource type">{TYPES.map(item => <button type="button" role="tab" aria-selected={!subjectMode && type === item} className={!subjectMode && type === item ? "active" : ""} key={item} onClick={() => switchType(item)}>{item.replaceAll("_", " ")}</button>)}<button type="button" role="tab" aria-selected={subjectMode} className={subjectMode ? "active" : ""} onClick={() => setSubjectMode(true)}>SUBJECTS</button></div>
    {subjectMode ? <div className="resource-admin-grid"><form className="panel resource-form" onSubmit={saveSubject}><div className="panel-heading"><h2>{subjectDraft.id ? "Edit subject" : "New subject"}</h2><button type="submit">Save</button></div><label className="field"><span>Preferred label</span><input required value={subjectDraft.label} onChange={event => setSubjectDraft({ ...subjectDraft, label: event.target.value, slug: slugify(event.target.value) })} /></label><label className="field"><span>Slug</span><input required value={subjectDraft.slug} onChange={event => setSubjectDraft({ ...subjectDraft, slug: slugify(event.target.value) })} /></label><label className="field"><span>Alternative labels</span><input value={subjectDraft.alternativeLabels.join(", ")} onChange={event => setSubjectDraft({ ...subjectDraft, alternativeLabels: event.target.value.split(",").map(v => v.trim()).filter(Boolean) })} /></label><label className="field"><span>Broader subject</span><select value={subjectDraft.broaderSubjectId} onChange={event => setSubjectDraft({ ...subjectDraft, broaderSubjectId: event.target.value })}><option value="">Top level</option>{subjects.filter(s => s.id !== subjectDraft.id).map(s => <option key={s.id} value={s.id}>{s.label}</option>)}</select></label><label className="field"><span>Description</span><textarea rows={5} value={subjectDraft.description} onChange={event => setSubjectDraft({ ...subjectDraft, description: event.target.value })} /></label><RelationPicker label="Related guides" items={guides.map(g => ({ id: g.id, label: g.title }))} values={subjectDraft.guideIds} onChange={values => setSubjectDraft({ ...subjectDraft, guideIds: values })} /></form><section className="resource-record-list">{subjects.map(subject => <button type="button" className="resource-record" key={subject.id} onClick={() => setSubjectDraft({ id: subject.id, label: subject.label, slug: subject.slug, alternativeLabels: subject.alternativeLabels, description: subject.description, notation: subject.notation || "", broaderSubjectId: subject.broaderSubjectId || "", relatedSubjectIds: subject.relatedSubjectIds, guideIds: subject.guideIds, active: subject.active })}><strong>{subject.label}</strong><span>{subject.broaderLabel ? `Within ${subject.broaderLabel}` : "Top-level subject"}</span></button>)}</section></div> :
    <div className="resource-admin-grid"><form className="panel resource-form" onSubmit={saveResource}><div className="panel-heading"><div><p className="eyebrow">{type.replaceAll("_", " ")}</p><h2>{draft.id ? "Edit record" : "New record"}</h2></div><button type="submit">Save</button></div><div className="form-grid"><label className="field"><span>Title</span><input required value={draft.title} onChange={event => setDraft({ ...draft, title: event.target.value, slug: slugify(event.target.value) })} /></label><label className="field"><span>Slug</span><input required value={draft.slug} onChange={event => setDraft({ ...draft, slug: slugify(event.target.value) })} /></label><label className="field"><span>Status</span><select value={draft.status} onChange={event => setDraft({ ...draft, status: event.target.value })}><option>DRAFT</option><option>PUBLISHED</option><option>ARCHIVED</option></select></label>{type !== "FAQ" ? <label className="field"><span>Alternative title</span><input value={draft.alternativeTitle || ""} onChange={event => setDraft({ ...draft, alternativeTitle: event.target.value })} /></label> : null}</div><label className="field"><span>{type === "LIBRARIAN" ? "Biography" : "Description"}</span><textarea rows={4} value={draft.description} onChange={event => setDraft({ ...draft, description: event.target.value })} /></label>
      {type === "DATABASE" ? <><div className="form-grid"><label className="field"><span>Database URL</span><input required type="url" value={draft.canonicalUrl || ""} onChange={event => setDraft({ ...draft, canonicalUrl: event.target.value })} /></label><label className="field"><span>Provider</span><input value={draft.provider || ""} onChange={event => setDraft({ ...draft, provider: event.target.value })} /></label><label className="field"><span>Access type</span><input value={draft.accessType || ""} onChange={event => setDraft({ ...draft, accessType: event.target.value })} /></label><label className="field"><span>Proxy</span><select value={draft.proxyMode} onChange={event => setDraft({ ...draft, proxyMode: event.target.value })}><option>NONE</option><option>EZPROXY</option><option>OPENATHENS</option><option>SHIBBOLETH</option><option>INSTITUTIONAL</option></select></label></div><div className="check-row"><label className="check-field"><input type="checkbox" checked={draft.openAccess} onChange={event => setDraft({ ...draft, openAccess: event.target.checked })} />Open access</label><label className="check-field"><input type="checkbox" checked={draft.featured} onChange={event => setDraft({ ...draft, featured: event.target.checked })} />Featured</label></div></> : null}
      {type === "LIBRARIAN" ? <div className="form-grid"><label className="field"><span>Email</span><input type="email" value={draft.email || ""} onChange={event => setDraft({ ...draft, email: event.target.value })} /></label><label className="field"><span>Phone</span><input value={draft.phone || ""} onChange={event => setDraft({ ...draft, phone: event.target.value })} /></label><label className="field"><span>ORCID</span><input value={draft.orcid || ""} onChange={event => setDraft({ ...draft, orcid: event.target.value })} /></label></div> : null}
      {type === "FAQ" ? <label className="field"><span>Answer</span><textarea required rows={8} value={draft.faqAnswer || ""} onChange={event => setDraft({ ...draft, faqAnswer: event.target.value })} /></label> : null}
      {type === "BOOK" ? <><div className="form-grid"><label className="field"><span>Authors</span><input value={draft.authors.join(", ")} onChange={event => setDraft({ ...draft, authors: event.target.value.split(",").map(v => v.trim()).filter(Boolean) })} /></label><label className="field"><span>ISBN</span><input value={draft.isbn || ""} onChange={event => setDraft({ ...draft, isbn: event.target.value })} /></label><label className="field"><span>Publisher</span><input value={draft.publisher || ""} onChange={event => setDraft({ ...draft, publisher: event.target.value })} /></label><label className="field"><span>Catalogue or ebook URL</span><input type="url" value={draft.canonicalUrl || ""} onChange={event => setDraft({ ...draft, canonicalUrl: event.target.value })} /></label></div></> : null}
      {type === "READING_LIST" ? <RelationPicker label="Books" items={books.map(book => ({ id: book.id, label: book.title }))} values={draft.items.map(item => item.bookId)} onChange={values => setDraft({ ...draft, items: values.map(bookId => ({ bookId, category: "RECOMMENDED", notes: "" })) })} /> : null}
      <RelationPicker label="Subjects" items={subjects.map(s => ({ id: s.id, label: s.label }))} values={draft.subjectIds} onChange={values => setDraft({ ...draft, subjectIds: values })} /><RelationPicker label="Related guides" items={guides.map(g => ({ id: g.id, label: g.title }))} values={draft.guideIds} onChange={values => setDraft({ ...draft, guideIds: values })} /></form>
      <section><label className="field resource-search"><span>Filter records</span><input type="search" value={query} onChange={event => setQuery(event.target.value)} /></label><div className="resource-record-list">{visible.map(item => <button type="button" className="resource-record" key={item.id} onClick={() => setDraft({ ...item })}><span className="status-label">{item.status}</span><strong>{item.title}</strong><span>{item.description || item.provider || "No description"}</span></button>)}{!visible.length ? <div className="empty-state"><h2>No records</h2></div> : null}</div></section></div>}
  </main>;
}

function RelationPicker({ label, items, values, onChange }: { label: string; items: Array<{ id: string; label: string }>; values: string[]; onChange: (values: string[]) => void }) {
  return <fieldset className="relation-picker"><legend>{label}</legend>{items.length ? items.map(item => <label className="check-field" key={item.id}><input type="checkbox" checked={values.includes(item.id)} onChange={() => onChange(values.includes(item.id) ? values.filter(value => value !== item.id) : [...values, item.id])} />{item.label}</label>) : <span className="muted">No records available</span>}</fieldset>;
}
