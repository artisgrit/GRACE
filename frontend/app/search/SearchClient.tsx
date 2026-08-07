"use client";

import { FormEvent, useEffect, useState } from "react";

type Item = { id: string; type: string; title: string; description: string; url: string; subjects: string[]; tags: string[] };
type Results = { query: string; total: number; results: Item[]; facets: { types: Record<string, number>; subjects: Record<string, number> }; degraded: boolean; source: string };
const empty: Results = { query: "", total: 0, results: [], facets: { types: {}, subjects: {} }, degraded: false, source: "opensearch" };

export default function SearchClient({ tenant }: { tenant: string }) {
  const [query, setQuery] = useState("");
  const [type, setType] = useState("");
  const [subject, setSubject] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [data, setData] = useState<Results>(empty);
  const [message, setMessage] = useState("Search across published GRACE content");

  async function run(event?: FormEvent) {
    event?.preventDefault();
    setMessage("Searching...");
    const params = new URLSearchParams({ q: query });
    if (type) params.set("type", type);
    if (subject) params.set("subject", subject);
    try {
      const response = await fetch(`/api/grace/public/tenants/${tenant}/search?${params}`);
      if (!response.ok) throw new Error("Search is temporarily unavailable");
      const next = await response.json() as Results;
      setData(next);
      setMessage(`${next.total} result${next.total === 1 ? "" : "s"}${next.degraded ? " - using resilient database search" : ""}`);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Search is temporarily unavailable"); }
  }

  useEffect(() => {
    if (query.trim().length < 2) { setSuggestions([]); return; }
    const timer = window.setTimeout(async () => {
      const response = await fetch(`/api/grace/public/tenants/${tenant}/search/autocomplete?q=${encodeURIComponent(query)}`);
      if (response.ok) setSuggestions(await response.json() as string[]);
    }, 250);
    return () => window.clearTimeout(timer);
  }, [query, tenant]);

  return <>
    <form className="unified-search" onSubmit={run}>
      <label className="field search-query"><span>Keywords</span><input type="search" value={query} list="search-suggestions" onChange={event => setQuery(event.target.value)} placeholder="Search guides, databases, books, FAQs..." /><datalist id="search-suggestions">{suggestions.map(value => <option key={value} value={value} />)}</datalist></label>
      <label className="field"><span>Content type</span><select value={type} onChange={event => setType(event.target.value)}><option value="">All types</option>{Object.keys(data.facets.types).map(value => <option key={value} value={value}>{value.replaceAll("_", " ")} ({data.facets.types[value]})</option>)}</select></label>
      <label className="field"><span>Subject</span><select value={subject} onChange={event => setSubject(event.target.value)}><option value="">All subjects</option>{Object.keys(data.facets.subjects).map(value => <option key={value} value={value}>{value} ({data.facets.subjects[value]})</option>)}</select></label>
      <button type="submit">Search</button>
    </form>
    <p className="result-count" role="status">{message}</p>
    <section className="search-results" aria-label="Unified search results">
      {data.results.map(item => <article key={`${item.type}-${item.id}`} className="search-result">
        <span className="status-label">{item.type.replaceAll("_", " ")}</span>
        <h2><a href={item.url}>{item.title}</a></h2>
        <p>{item.description}</p>
        {item.subjects.length ? <div className="tag-row">{item.subjects.map(value => <span key={value}>{value}</span>)}</div> : null}
      </article>)}
      {data.query && !data.results.length ? <div className="empty-state"><h2>No matching content</h2><p>Try fewer keywords or remove a filter.</p></div> : null}
    </section>
  </>;
}
