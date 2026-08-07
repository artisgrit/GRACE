import type { GuideSummary } from "../../lib/guide-types";

const backendApi = process.env.BACKEND_API_URL ?? "http://localhost:8080/api/v1";
const tenant = process.env.GRACE_TENANT_SLUG ?? "default";

export const dynamic = "force-dynamic";

export default async function PublicGuidesPage() {
  const response = await fetch(`${backendApi}/public/tenants/${tenant}/guides`, { cache: "no-store" });
  const guides = response.ok ? await response.json() as GuideSummary[] : [];
  return (
    <main className="public-guides-shell">
      <header className="public-list-header"><div><p className="eyebrow">Library research support</p><h1>Guides</h1><p>Curated starting points, specialist resources, and practical research guidance.</p></div><a className="nav-link" href="/">Home</a></header>
      <section className="public-guide-list" aria-label="Published guides">
        {guides.map(guide => <article key={guide.id}><span className="status-label">{guide.guideTypeName}</span><h2><a href={`/guides/${guide.slug}`}>{guide.title}</a></h2><p>{guide.description}</p><small>Maintained by {guide.ownerName}</small></article>)}
        {!guides.length ? <div className="empty-state"><h2>No published guides yet</h2><p>Published library guides will appear here.</p></div> : null}
      </section>
    </main>
  );
}
