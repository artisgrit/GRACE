"use client";

import { useEffect, useState } from "react";
type Summary = { days: number; totals: Record<string,number>; popularContent: {path:string;views:number}[]; trend:{day:string;total:number}[] };
type Assessment = { guideId:string;title:string;slug:string;healthScore:number;accessibilityIssues:number;brokenLinks:number;reviewOverdue:boolean;issues:{code:string;message:string}[];assessedAt:string };
type CurrentUser = { tenantSlug:string;displayName:string };
async function request<T>(path:string,init?:RequestInit){const response=await fetch(`/api/grace${path}`,init);if(response.status===401){window.location.assign("/login");throw new Error("Session expired");}if(!response.ok)throw new Error(await response.text());return response.json() as Promise<T>;}

export default function QualityDashboard(){
  const [tenant,setTenant]=useState("");const [summary,setSummary]=useState<Summary|null>(null);const [guides,setGuides]=useState<Assessment[]>([]);const [message,setMessage]=useState("Loading operational quality...");
  async function load(){try{const me=await (await fetch("/api/auth/me")).json() as CurrentUser;setTenant(me.tenantSlug);const [analytics,quality]=await Promise.all([request<Summary>(`/tenants/${me.tenantSlug}/analytics/summary?days=30`),request<Assessment[]>(`/tenants/${me.tenantSlug}/quality/guides`)]);setSummary(analytics);setGuides(quality);setMessage("Operational quality loaded");}catch(error){setMessage(error instanceof Error?error.message:"Could not load quality data");}}
  useEffect(()=>{void load();},[]);
  async function run(){setMessage("Checking links and assessing published guides...");const result=await request<{linksChecked:number;guidesAssessed:number;remindersCreated:number}>(`/tenants/${tenant}/quality/run`,{method:"POST"});setMessage(`Checked ${result.linksChecked} links and assessed ${result.guidesAssessed} guides`);await load();}
  const totalEvents=Object.values(summary?.totals??{}).reduce((sum,value)=>sum+value,0);
  return <main className="workspace-shell"><header className="workspace-header"><div><p className="eyebrow">Analytics and content quality</p><h1>Operational health</h1><p>Aggregate usage, link status, review readiness, and accessibility checks.</p></div><nav className="admin-actions"><a className="nav-link" href="/admin">Configuration</a><a className="nav-link" href="/">Public site</a><button type="button" onClick={run} disabled={!tenant}>Run checks</button></nav></header><p className="notice" role="status">{message}</p>
    <section className="metric-grid" aria-label="Thirty day analytics"><div><span>Events</span><strong>{totalEvents}</strong></div><div><span>Page views</span><strong>{summary?.totals.PAGE_VIEW??0}</strong></div><div><span>Guide views</span><strong>{summary?.totals.GUIDE_VIEW??0}</strong></div><div><span>Searches</span><strong>{summary?.totals.SEARCH??0}</strong></div></section>
    <section className="quality-layout"><div><h2>Guide health</h2><div className="quality-list">{guides.map(guide=><article key={guide.guideId}><div className="score" data-level={guide.healthScore<60?"low":guide.healthScore<85?"medium":"high"}>{guide.healthScore}</div><div><h3><a href={`/admin/guides/${guide.guideId}`}>{guide.title}</a></h3><p>{guide.issues.length?guide.issues.map(issue=>issue.message).join("; "):"No current quality issues"}</p><small>{guide.brokenLinks} broken links | {guide.accessibilityIssues} accessibility issues{guide.reviewOverdue?" | Review overdue":""}</small></div></article>)}{!guides.length?<p className="empty-state">Run checks to create the first quality assessment.</p>:null}</div></div>
    <aside><h2>Popular content</h2><ol className="popular-list">{summary?.popularContent.map(item=><li key={item.path}><a href={item.path}>{item.path}</a><span>{item.views}</span></li>)}</ol></aside></section>
  </main>;
}
