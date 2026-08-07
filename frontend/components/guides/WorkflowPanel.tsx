"use client";

import { useEffect, useState } from "react";
import type { WorkflowState } from "../../lib/guide-types";

type User = { id: string; displayName: string; email: string };

async function request<T>(path: string, init?: RequestInit) {
  const response = await fetch(`/api/grace${path}`, { ...init, headers: { "content-type": "application/json", ...(init?.headers ?? {}) } });
  if (!response.ok) throw new Error(await response.text());
  return response.json() as Promise<T>;
}

function iso(value: string) { return value ? new Date(value).toISOString() : null; }

export default function WorkflowPanel({ tenant, guideId, permissions, onChange }: {
  tenant: string; guideId: string; permissions: string[]; onChange: (status: string, version: number) => void;
}) {
  const [state, setState] = useState<WorkflowState | null>(null);
  const [users, setUsers] = useState<User[]>([]);
  const [comment, setComment] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [nextReviewAt, setNextReviewAt] = useState("");
  const [reviewerId, setReviewerId] = useState("");
  const [message, setMessage] = useState("Loading workflow...");

  async function load() {
    const next = await request<WorkflowState>(`/tenants/${tenant}/guides/${guideId}/workflow`);
    setState(next);
    if (permissions.includes("guide.review")) {
      const nextUsers = await request<User[]>(`/tenants/${tenant}/users`);
      setUsers(nextUsers); setReviewerId(current => current || nextUsers[0]?.id || "");
    }
    setMessage("Workflow current");
  }
  useEffect(() => { void load().catch(error => setMessage(error instanceof Error ? error.message : "Workflow unavailable")); }, [tenant, guideId]);

  async function transition(action: string) {
    if (!state) return;
    setMessage(`Applying ${action.toLowerCase().replaceAll("_", " ")}...`);
    try {
      const next = await request<WorkflowState>(`/tenants/${tenant}/guides/${guideId}/workflow/transitions`, {
        method: "POST",
        body: JSON.stringify({ action, comment, scheduledAt: iso(scheduledAt), expiresAt: iso(expiresAt), nextReviewAt: iso(nextReviewAt), version: state.version })
      });
      setState(next); setComment(""); onChange(next.status, next.version);
      setMessage(`Guide is now ${next.status.replaceAll("_", " ")}`);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Transition failed"); }
  }

  async function addComment() {
    if (!comment.trim()) return;
    const next = await request<WorkflowState>(`/tenants/${tenant}/guides/${guideId}/workflow/comments`, { method: "POST", body: JSON.stringify({ body: comment }) });
    setState(next); setComment(""); setMessage("Comment added");
  }

  async function assignReviewer() {
    if (!reviewerId) return;
    const next = await request<WorkflowState>(`/tenants/${tenant}/guides/${guideId}/workflow/reviewers`, { method: "POST", body: JSON.stringify({ reviewerId }) });
    setState(next); setMessage("Reviewer assigned");
  }

  if (!state) return <p className="notice" role="status">{message}</p>;
  const canEdit = permissions.includes("guide.edit");
  const canReview = permissions.includes("guide.review");
  const canPublish = permissions.includes("guide.publish");
  const canArchive = permissions.includes("guide.archive");
  return <section className="workflow-layout">
    <div className="workflow-main">
      <section className="panel workflow-status"><div><p className="eyebrow">Editorial status</p><h2>{state.status.replaceAll("_", " ")}</h2></div><div className="workflow-actions">
        {(state.status === "DRAFT" || state.status === "REVIEW_DUE") && canEdit ? <button type="button" onClick={() => void transition("SUBMIT")}>Submit for review</button> : null}
        {state.status === "IN_REVIEW" && canReview ? <><button type="button" onClick={() => void transition("APPROVE")}>Approve</button><button className="secondary-button" type="button" onClick={() => void transition("REQUEST_CHANGES")}>Request changes</button><button className="danger-button" type="button" onClick={() => void transition("REJECT")}>Reject</button></> : null}
        {state.status === "APPROVED" && canPublish ? <><button type="button" onClick={() => void transition("PUBLISH")}>Publish</button><button className="secondary-button" type="button" onClick={() => void transition("SCHEDULE")}>Schedule</button></> : null}
        {state.status === "SCHEDULED" && canPublish ? <button type="button" onClick={() => void transition("PUBLISH")}>Publish now</button> : null}
        {state.status !== "ARCHIVED" && canArchive ? <button className="danger-button" type="button" onClick={() => void transition("ARCHIVE")}>Archive</button> : null}
      </div></section>
      <section className="panel"><h2>Review note</h2><label className="field"><span>Comment</span><textarea rows={4} value={comment} onChange={event => setComment(event.target.value)} /></label><button className="secondary-button" type="button" onClick={() => void addComment()} disabled={!comment.trim()}>Add comment</button></section>
      <section className="panel"><h2>Activity</h2><div className="timeline-list">{state.events.map(event => <article key={event.id}><strong>{event.action.replaceAll("_", " ")}</strong><span>{event.fromStatus.replaceAll("_", " ")} to {event.toStatus.replaceAll("_", " ")}</span>{event.comment ? <p>{event.comment}</p> : null}<small>{event.actorName} | {new Date(event.createdAt).toLocaleString()}</small></article>)}{!state.events.length ? <p>No workflow actions yet.</p> : null}</div></section>
    </div>
    <aside className="workflow-sidebar">
      <section className="panel"><h2>Dates</h2><label className="field"><span>Publish at</span><input type="datetime-local" value={scheduledAt} onChange={event => setScheduledAt(event.target.value)} /></label><label className="field"><span>Expire at</span><input type="datetime-local" value={expiresAt} onChange={event => setExpiresAt(event.target.value)} /></label><label className="field"><span>Next review</span><input type="datetime-local" value={nextReviewAt} onChange={event => setNextReviewAt(event.target.value)} /></label></section>
      {canReview ? <section className="panel"><h2>Reviewers</h2><div className="reviewer-list">{state.reviewers.map(reviewer => <span key={reviewer.id}>{reviewer.displayName}</span>)}</div><label className="field"><span>Assign reviewer</span><select value={reviewerId} onChange={event => setReviewerId(event.target.value)}>{users.map(user => <option key={user.id} value={user.id}>{user.displayName}</option>)}</select></label><button className="secondary-button" type="button" onClick={() => void assignReviewer()}>Assign</button></section> : null}
      <section className="panel"><h2>Comments</h2><div className="review-comment-list">{state.comments.map(item => <article key={item.id}><p>{item.body}</p><small>{item.authorName} | {new Date(item.createdAt).toLocaleString()}</small></article>)}</div></section>
    </aside>
    <p className="notice workflow-message" role="status">{message}</p>
  </section>;
}
