import SearchClient from "./SearchClient";

const tenant = process.env.GRACE_TENANT_SLUG ?? "default";
export default function SearchPage() {
  return <main className="public-guides-shell search-shell">
    <header className="public-list-header"><div><p className="eyebrow">Unified discovery</p><h1>Search GRACE</h1><p>Search published guides, databases, books, reading lists, specialists, and library answers.</p></div><nav className="admin-actions"><a className="nav-link" href="/guides">Guides</a><a className="nav-link" href="/resources">Resources</a><a className="nav-link" href="/">Home</a></nav></header>
    <SearchClient tenant={tenant} />
  </main>;
}
