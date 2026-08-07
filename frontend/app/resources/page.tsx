import type { LibraryResource, Subject } from "../../lib/resource-types";

const backendApi = process.env.BACKEND_API_URL ?? "http://localhost:8080/api/v1";
const tenant = process.env.GRACE_TENANT_SLUG ?? "default";
const TYPES = ["ALL", "DATABASE", "BOOK", "READING_LIST", "LIBRARIAN", "FAQ", "SUBJECT"];
export const dynamic = "force-dynamic";

export default async function ResourcesPage({ searchParams }: { searchParams: Promise<{ q?: string; type?: string }> }) {
  const params = await searchParams;
  const query = params.q?.trim() ?? "";
  const type = TYPES.includes(params.type ?? "") ? params.type! : "ALL";
  const resourceUrl = new URL(`${backendApi}/public/tenants/${tenant}/resources`);
  if (query) resourceUrl.searchParams.set("q", query);
  if (type !== "ALL" && type !== "SUBJECT") resourceUrl.searchParams.set("type", type);
  const [resourceResponse, subjectResponse] = await Promise.all([
    fetch(resourceUrl, { cache: "no-store" }),
    fetch(`${backendApi}/public/tenants/${tenant}/subjects`, { cache: "no-store" })
  ]);
  const allResources = resourceResponse.ok ? await resourceResponse.json() as LibraryResource[] : [];
  const subjects = subjectResponse.ok ? await subjectResponse.json() as Subject[] : [];
  const resources = type === "SUBJECT" ? [] : allResources;
  const visibleSubjects = type === "SUBJECT" || type === "ALL" ? subjects.filter(subject => `${subject.label} ${subject.description} ${subject.alternativeLabels.join(" ")}`.toLowerCase().includes(query.toLowerCase())) : [];
  const bookNames = new Map(allResources.filter(item => item.resourceType === "BOOK").map(item => [item.id, item.title]));

  return <main className="public-guides-shell resource-directory">
    <header className="public-list-header"><div><p className="eyebrow">Library collections and expertise</p><h1>Resources</h1><p>Find databases, books, reading lists, subject specialists, and answers from the library.</p></div><nav className="admin-actions"><a className="nav-link" href="/guides">Guides</a><a className="nav-link" href="/">Home</a></nav></header>
    <form className="directory-search" method="get"><label className="field"><span>Search resources</span><input type="search" name="q" defaultValue={query} /></label><label className="field"><span>Resource type</span><select name="type" defaultValue={type}>{TYPES.map(item => <option key={item} value={item}>{item.replaceAll("_", " ")}</option>)}</select></label><button type="submit">Search</button></form>
    <p className="result-count" aria-live="polite">{resources.length + visibleSubjects.length} results</p>
    <section className="directory-results" aria-label="Library resource results">
      {visibleSubjects.map(subject => <article className="directory-record" key={subject.id}><span className="status-label">Subject</span><h2>{subject.label}</h2>{subject.broaderLabel ? <small>Within {subject.broaderLabel}</small> : null}<p>{subject.description}</p>{subject.alternativeLabels.length ? <div className="tag-row">{subject.alternativeLabels.map(label => <span key={label}>{label}</span>)}</div> : null}</article>)}
      {resources.map(resource => <article className="directory-record" key={resource.id}><span className="status-label">{resource.resourceType.replaceAll("_", " ")}</span><h2>{resource.canonicalUrl ? <a href={resource.canonicalUrl}>{resource.title}</a> : resource.title}</h2>{resource.provider ? <small>{resource.provider}</small> : null}<p>{resource.description}</p>{resource.resourceType === "FAQ" ? <div className="faq-answer">{resource.faqAnswer}</div> : null}{resource.resourceType === "BOOK" && resource.authors.length ? <p className="record-meta">{resource.authors.join(", ")}{resource.isbn ? ` | ISBN ${resource.isbn}` : ""}</p> : null}{resource.resourceType === "LIBRARIAN" ? <div className="record-links">{resource.email ? <a href={`mailto:${resource.email}`}>{resource.email}</a> : null}{resource.orcid ? <a href={`https://orcid.org/${resource.orcid}`}>ORCID</a> : null}</div> : null}{resource.resourceType === "READING_LIST" && resource.items.length ? <ul>{resource.items.map(item => <li key={item.bookId}>{bookNames.get(item.bookId) ?? "Book"} <small>{item.category.replaceAll("_", " ")}</small></li>)}</ul> : null}{resource.openAccess ? <span className="access-label">Open access</span> : null}</article>)}
      {!resources.length && !visibleSubjects.length ? <div className="empty-state"><h2>No matching resources</h2><p>Try another search term or resource type.</p></div> : null}
    </section>
  </main>;
}
