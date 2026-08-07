# Guide Management

GRACE Phase 2 provides tenant-scoped guide authoring, templates, structured content blocks,
autosave, previews, and immutable revision history. Guide content is stored as JSON objects;
the API does not accept arbitrary HTML.

## Data model

Flyway migration `V3__guide_management.sql` creates guide types, tenant templates, guides,
co-editors, subjects, pages, reusable blocks, page blocks, and revision snapshots. Aggregate
updates are transactional and guarded by an optimistic `version` field. Every successful save
increments both the version and revision number and records a complete restorable snapshot.

The default tenant receives Subject, Course, and Research guide templates. The schema supports
the full block catalog while the current visual editor exposes Heading, Rich text, Callout,
Link list, and Code blocks.

## Authenticated API

- `GET /api/v1/tenants/{tenant}/guide-types`
- `GET /api/v1/tenants/{tenant}/guide-templates`
- `GET|POST /api/v1/tenants/{tenant}/guides`
- `GET|PUT /api/v1/tenants/{tenant}/guides/{id}`
- `GET /api/v1/tenants/{tenant}/guides/{id}/revisions`
- `POST /api/v1/tenants/{tenant}/guides/{id}/revisions/{revision}/restore`

Creation requires `guide.create`; editing and revision restore require `guide.edit`. Tenant
membership is checked for every request. Contributors may only change guides they own or
co-edit, while users with `guide.review` or `guide.publish` may manage all guides in the tenant.

## Public API

- `GET /api/v1/public/tenants/{tenant}/guides`
- `GET /api/v1/public/tenants/{tenant}/guides/{slug}`

Only records in `PUBLISHED` status are returned publicly. Drafts and editorial revisions are
never exposed by these routes.

## Editor

The guide workspace is available at `/admin/guides`. Authors can create from a template, edit
metadata, add and reorder pages and blocks, preview the rendered guide, and restore revisions.
Changes autosave after a short idle period, with an explicit Save action also available.

Public guides are listed at `/guides` and rendered at `/guides/{slug}`. Publishing workflow,
approval transitions, scheduling, co-editor administration, and the remaining block editors are
the next implementation milestone.
