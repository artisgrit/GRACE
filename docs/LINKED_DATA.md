# Linked Data

GRACE Phase 9 publishes each tenant's approved public knowledge as a standards-based RDF graph. The graph is generated from authoritative GRACE records, so guide publishing, subject curation, reading lists, and liaison assignments remain the only content-management workflows.

## Representations

The linked-data endpoints support HTTP content negotiation and an optional `format` query parameter:

| Representation | Media type | Format value |
|---|---|---|
| JSON-LD 1.1 | `application/ld+json` | `jsonld` |
| Turtle | `text/turtle` | `turtle` or `ttl` |
| RDF/XML | `application/rdf+xml` | `rdfxml` |

Examples:

```text
GET /api/v1/public/tenants/default/semantic/graph
GET /api/v1/public/tenants/default/semantic/subjects?format=turtle
GET /api/v1/public/tenants/default/semantic/entities/guide/{uuid}
GET /api/v1/public/tenants/default/semantic/entities/resource/{uuid}?format=rdfxml
```

Responses include `Vary: Accept`, a content-derived ETag, and no-cache revalidation so consumers receive current published data.

## Vocabulary and Identifiers

- Schema.org describes guides, books, databases, FAQs, people, and reading lists.
- SKOS describes each tenant's concept scheme, preferred and alternative labels, definitions, broader concepts, and related concepts.
- Dublin Core Terms carries canonical `urn:uuid:` identifiers.
- GRACE terms identify guide/resource classifications and liaison relationships.
- Every published entity receives a dereferenceable canonical HTTPS/HTTP URI under its tenant semantic endpoint.

Set `GRACE_PUBLIC_BASE_URL` to the externally visible backend origin in production. Canonical URIs must not use an internal container hostname.

## Relationships

The graph derives relationships from existing tenant-isolated tables:

- guide and resource subjects become `schema:about`
- guide recommendations become `schema:hasPart`
- reading-list entries become `schema:itemListElement`
- subject hierarchy and associations become `skos:broader` and `skos:related`
- subject librarians become `grace:liaison`

Only active subjects and published, non-deleted guides/resources are exported. Draft and archived content never enters the public graph.
