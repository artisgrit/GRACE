"use client";

import { useEffect, useRef, useState } from "react";
import GuideRenderer from "../../../../components/guides/GuideRenderer";
import WorkflowPanel from "../../../../components/guides/WorkflowPanel";
import type { GuideBlock, GuideDetail, GuidePage, GuideRevision, GuideType } from "../../../../lib/guide-types";

type CurrentUser = { tenantSlug: string; permissions: string[] };
type EditorMode = "edit" | "preview" | "history" | "workflow";

async function request<T>(path: string, init?: RequestInit) {
  const response = await fetch(`/api/grace${path}`, { ...init, headers: { "content-type": "application/json", ...(init?.headers ?? {}) } });
  if (response.status === 401) window.location.assign("/login");
  if (!response.ok) throw new Error(await response.text());
  return response.json() as Promise<T>;
}

function slugify(value: string) {
  return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function newBlock(blockType: string): GuideBlock {
  const content: Record<string, unknown> = blockType === "HEADING" ? { text: "New heading", level: 2 }
    : blockType === "CALLOUT" ? { title: "Research tip", text: "" }
    : blockType === "LINK_LIST" ? { title: "Useful links", items: [] }
    : { text: "" };
  return { id: crypto.randomUUID(), blockType, position: 0, content, settings: blockType === "CALLOUT" ? { tone: "information" } : {} };
}

function BlockEditor({ block, onChange, onRemove, onMoveUp, onMoveDown, first, last }: {
  block: GuideBlock;
  onChange: (next: GuideBlock) => void;
  onRemove: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
  first: boolean;
  last: boolean;
}) {
  const content = block.content;
  const setContent = (patch: Record<string, unknown>) => onChange({ ...block, content: { ...content, ...patch } });
  const links = Array.isArray(content.items) ? content.items as Array<{ label: string; url: string }> : [];
  return (
    <article className="block-editor">
      <header><strong>{block.blockType.replaceAll("_", " ")}</strong><div className="block-actions"><button type="button" className="small-button" onClick={onMoveUp} disabled={first}>Move up</button><button type="button" className="small-button" onClick={onMoveDown} disabled={last}>Move down</button><button type="button" className="danger-button small-button" onClick={onRemove}>Remove</button></div></header>
      {block.blockType === "HEADING" ? <div className="form-grid"><label className="field"><span>Heading</span><input value={String(content.text ?? "")} onChange={event => setContent({ text: event.target.value })} /></label><label className="field"><span>Level</span><select value={Number(content.level ?? 2)} onChange={event => setContent({ level: Number(event.target.value) })}><option value={2}>Heading 2</option><option value={3}>Heading 3</option><option value={4}>Heading 4</option></select></label></div> : null}
      {block.blockType === "RICH_TEXT" || block.blockType === "CODE" ? <label className="field"><span>{block.blockType === "CODE" ? "Code" : "Text"}</span><textarea rows={6} value={String(content.text ?? "")} onChange={event => setContent({ text: event.target.value })} /></label> : null}
      {block.blockType === "CALLOUT" ? <><div className="form-grid"><label className="field"><span>Title</span><input value={String(content.title ?? "")} onChange={event => setContent({ title: event.target.value })} /></label><label className="field"><span>Tone</span><select value={String(block.settings.tone ?? "information")} onChange={event => onChange({ ...block, settings: { ...block.settings, tone: event.target.value } })}><option value="information">Information</option><option value="success">Success</option><option value="warning">Warning</option></select></label></div><label className="field"><span>Text</span><textarea rows={4} value={String(content.text ?? "")} onChange={event => setContent({ text: event.target.value })} /></label></> : null}
      {block.blockType === "LINK_LIST" ? <><label className="field"><span>List title</span><input value={String(content.title ?? "")} onChange={event => setContent({ title: event.target.value })} /></label><div className="link-edit-list">{links.map((link, index) => <div key={index}><label className="field"><span>Label</span><input value={link.label} onChange={event => setContent({ items: links.map((item, itemIndex) => itemIndex === index ? { ...item, label: event.target.value } : item) })} /></label><label className="field"><span>URL</span><input type="url" value={link.url} onChange={event => setContent({ items: links.map((item, itemIndex) => itemIndex === index ? { ...item, url: event.target.value } : item) })} /></label><button className="danger-button small-button" type="button" onClick={() => setContent({ items: links.filter((_, itemIndex) => itemIndex !== index) })}>Remove link</button></div>)}</div><button className="secondary-button small-button" type="button" onClick={() => setContent({ items: [...links, { label: "", url: "" }] })}>Add link</button></> : null}
    </article>
  );
}

export default function GuideEditor({ guideId }: { guideId: string }) {
  const [tenant, setTenant] = useState("");
  const [permissions, setPermissions] = useState<string[]>([]);
  const [guide, setGuide] = useState<GuideDetail | null>(null);
  const [types, setTypes] = useState<GuideType[]>([]);
  const [revisions, setRevisions] = useState<GuideRevision[]>([]);
  const [selectedPageId, setSelectedPageId] = useState("");
  const [mode, setMode] = useState<EditorMode>("edit");
  const [message, setMessage] = useState("Loading guide...");
  const [changeSummary, setChangeSummary] = useState("Updated guide content");
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const sequence = useRef(0);

  async function load() {
    const meResponse = await fetch("/api/auth/me", { cache: "no-store" });
    if (!meResponse.ok) return window.location.assign("/login");
    const me = await meResponse.json() as CurrentUser;
    const [nextGuide, nextTypes, nextRevisions] = await Promise.all([
      request<GuideDetail>(`/tenants/${me.tenantSlug}/guides/${guideId}`),
      request<GuideType[]>(`/tenants/${me.tenantSlug}/guide-types`),
      request<GuideRevision[]>(`/tenants/${me.tenantSlug}/guides/${guideId}/revisions`)
    ]);
    setTenant(me.tenantSlug); setPermissions(me.permissions); setGuide(nextGuide); setTypes(nextTypes); setRevisions(nextRevisions);
    setSelectedPageId(nextGuide.pages[0]?.id ?? ""); setDirty(false); setMessage("All changes saved");
  }

  useEffect(() => { void load().catch(error => setMessage(error instanceof Error ? error.message : "Could not load guide")); }, [guideId]);

  function change(updater: (current: GuideDetail) => GuideDetail) {
    sequence.current += 1;
    setGuide(current => current ? updater(current) : current);
    setDirty(true); setMessage("Unsaved changes");
  }

  async function persist(summary: string) {
    if (!guide || saving || !dirty) return;
    const savingSequence = sequence.current;
    const snapshot = guide;
    setSaving(true); setMessage("Saving...");
    try {
      const saved = await request<GuideDetail>(`/tenants/${tenant}/guides/${snapshot.id}`, {
        method: "PUT",
        body: JSON.stringify({
          title: snapshot.title, slug: snapshot.slug, description: snapshot.description,
          guideTypeKey: snapshot.guideTypeKey, tags: snapshot.tags, metadata: snapshot.metadata,
          featuredImageUrl: snapshot.featuredImageUrl, seoTitle: snapshot.seoTitle,
          seoDescription: snapshot.seoDescription, version: snapshot.version,
          changeSummary: summary, pages: snapshot.pages
        })
      });
      if (sequence.current === savingSequence) {
        setGuide(saved); setDirty(false); setMessage("All changes saved");
      } else {
        setGuide(current => current ? { ...current, version: saved.version, revisionNumber: saved.revisionNumber } : saved);
        setMessage("Saving newer changes...");
      }
      setRevisions(await request<GuideRevision[]>(`/tenants/${tenant}/guides/${snapshot.id}/revisions`));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }

  useEffect(() => {
    if (!dirty || !guide || saving) return;
    const timer = window.setTimeout(() => { void persist("Autosaved changes"); }, 1500);
    return () => window.clearTimeout(timer);
  }, [dirty, guide, saving]);

  if (!guide) return <main className="workspace-shell"><p className="notice" role="status">{message}</p></main>;
  const currentGuide = guide;
  const pageIndex = Math.max(0, guide.pages.findIndex(page => page.id === selectedPageId));
  const page = guide.pages[pageIndex];

  function updatePage(nextPage: GuidePage) {
    change(current => ({ ...current, pages: current.pages.map(item => item.id === nextPage.id ? nextPage : item) }));
  }

  function movePage(offset: number) {
    const target = pageIndex + offset;
    if (target < 0 || target >= currentGuide.pages.length) return;
    change(current => { const pages = [...current.pages]; [pages[pageIndex], pages[target]] = [pages[target], pages[pageIndex]]; return { ...current, pages }; });
  }

  async function restore(revisionNumber: number) {
    if (!window.confirm(`Restore revision ${revisionNumber}?`)) return;
    const restored = await request<GuideDetail>(`/tenants/${tenant}/guides/${currentGuide.id}/revisions/${revisionNumber}/restore`, { method: "POST" });
    setGuide(restored); setSelectedPageId(restored.pages[0]?.id ?? ""); setDirty(false);
    setRevisions(await request<GuideRevision[]>(`/tenants/${tenant}/guides/${currentGuide.id}/revisions`));
    setMessage(`Revision ${revisionNumber} restored`);
  }

  return (
    <main className="editor-shell">
      <header className="editor-header"><div><a className="back-link" href="/admin/guides">Back to guides</a><h1>{guide.title}</h1><p>{guide.status.replaceAll("_", " ")} | Revision {guide.revisionNumber}</p></div><div className="editor-header-actions"><div className="segmented-control editor-modes" aria-label="Editor view"><button className={mode === "edit" ? "active" : ""} type="button" onClick={() => setMode("edit")}>Edit</button><button className={mode === "preview" ? "active" : ""} type="button" onClick={() => setMode("preview")}>Preview</button><button className={mode === "workflow" ? "active" : ""} type="button" onClick={() => setMode("workflow")}>Workflow</button><button className={mode === "history" ? "active" : ""} type="button" onClick={() => setMode("history")}>History</button></div><button type="button" onClick={() => void persist(changeSummary)} disabled={!dirty || saving}>Save</button></div></header>
      <p className="notice editor-notice" role="status">{message}</p>
      {mode === "preview" ? <GuideRenderer guide={guide} /> : null}
      {mode === "history" ? <section className="revision-list">{revisions.map(revision => <article key={revision.id}><div><strong>Revision {revision.revisionNumber}</strong><span>{revision.changeSummary}</span><small>{revision.authorName} | {new Date(revision.createdAt).toLocaleString()}</small></div><button className="secondary-button" type="button" onClick={() => void restore(revision.revisionNumber)} disabled={revision.revisionNumber === guide.revisionNumber}>Restore</button></article>)}</section> : null}
      {mode === "workflow" ? <WorkflowPanel tenant={tenant} guideId={guide.id} permissions={permissions} onChange={(status, version) => setGuide(current => current ? { ...current, status, version } : current)} /> : null}
      {mode === "edit" ? <div className="guide-editor-layout">
        <aside className="page-manager"><div className="page-manager-heading"><h2>Pages</h2><button className="small-button" type="button" onClick={() => { const id = crypto.randomUUID(); const title = `Page ${guide.pages.length + 1}`; change(current => ({ ...current, pages: [...current.pages, { id, title, slug: slugify(title), position: current.pages.length, layout: { columns: 1 }, blocks: [] }] })); setSelectedPageId(id); }}>Add page</button></div><div className="page-tabs">{guide.pages.map(item => <button type="button" key={item.id} className={item.id === page.id ? "active" : ""} onClick={() => setSelectedPageId(item.id)}>{item.title}</button>)}</div><div className="page-order"><button className="small-button secondary-button" type="button" onClick={() => movePage(-1)} disabled={pageIndex === 0}>Move page up</button><button className="small-button secondary-button" type="button" onClick={() => movePage(1)} disabled={pageIndex === guide.pages.length - 1}>Move page down</button></div></aside>
        <section className="editor-canvas"><section className="panel guide-settings"><div className="form-grid"><label className="field"><span>Guide title</span><input value={guide.title} onChange={event => change(current => ({ ...current, title: event.target.value }))} /></label><label className="field"><span>URL slug</span><input value={guide.slug} onChange={event => change(current => ({ ...current, slug: slugify(event.target.value) }))} /></label><label className="field"><span>Guide type</span><select value={guide.guideTypeKey} onChange={event => change(current => ({ ...current, guideTypeKey: event.target.value }))}>{types.map(type => <option key={type.id} value={type.key}>{type.displayName}</option>)}</select></label><label className="field"><span>Tags</span><input value={guide.tags.join(", ")} onChange={event => change(current => ({ ...current, tags: event.target.value.split(",").map(tag => tag.trim()).filter(Boolean) }))} /></label></div><label className="field"><span>Description</span><textarea rows={3} value={guide.description} onChange={event => change(current => ({ ...current, description: event.target.value }))} /></label></section>
        <section className="panel page-editor"><div className="panel-heading"><div><p className="eyebrow">Page {pageIndex + 1}</p><h2>{page.title}</h2></div>{guide.pages.length > 1 ? <button className="danger-button" type="button" onClick={() => { change(current => ({ ...current, pages: current.pages.filter(item => item.id !== page.id) })); setSelectedPageId(guide.pages.find(item => item.id !== page.id)?.id ?? ""); }}>Remove page</button> : null}</div><div className="form-grid"><label className="field"><span>Page title</span><input value={page.title} onChange={event => updatePage({ ...page, title: event.target.value })} /></label><label className="field"><span>Page slug</span><input value={page.slug} onChange={event => updatePage({ ...page, slug: slugify(event.target.value) })} /></label></div><div className="block-list">{page.blocks.map((block, index) => <BlockEditor key={block.id} block={block} first={index === 0} last={index === page.blocks.length - 1} onChange={next => updatePage({ ...page, blocks: page.blocks.map(item => item.id === block.id ? next : item) })} onRemove={() => updatePage({ ...page, blocks: page.blocks.filter(item => item.id !== block.id) })} onMoveUp={() => { const blocks = [...page.blocks]; [blocks[index - 1], blocks[index]] = [blocks[index], blocks[index - 1]]; updatePage({ ...page, blocks }); }} onMoveDown={() => { const blocks = [...page.blocks]; [blocks[index + 1], blocks[index]] = [blocks[index], blocks[index + 1]]; updatePage({ ...page, blocks }); }} />)}</div><div className="block-picker"><span>Add block</span>{["HEADING", "RICH_TEXT", "CALLOUT", "LINK_LIST", "CODE"].map(type => <button className="secondary-button small-button" type="button" key={type} onClick={() => updatePage({ ...page, blocks: [...page.blocks, newBlock(type)] })}>{type.replaceAll("_", " ")}</button>)}</div></section>
        <section className="panel save-summary"><label className="field"><span>Change summary</span><input value={changeSummary} onChange={event => setChangeSummary(event.target.value)} /></label><button type="button" onClick={() => void persist(changeSummary)} disabled={!dirty || saving}>Save guide</button></section></section>
      </div> : null}
    </main>
  );
}
