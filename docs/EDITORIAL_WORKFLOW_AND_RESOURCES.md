# Editorial Workflow and Library Resources

GRACE Phases 3 and 4 add a permission-controlled editorial state machine and a tenant-scoped
library resource catalogue. PostgreSQL remains the source of truth for workflow history,
subjects, guide relationships, and resource records.

## Editorial workflow

The default workflow is `DRAFT -> IN_REVIEW -> APPROVED -> SCHEDULED/PUBLISHED -> REVIEW_DUE
-> ARCHIVED`. Transitions use optimistic locking and enforce `guide.edit`, `guide.review`,
`guide.publish`, or `guide.archive` according to the action. Rejection and change requests
require a comment. Contributors cannot publish by changing a request payload.

Reviewers, review comments, transition events, submission and approval timestamps, scheduled
publication, expiry, and review dates are persisted. A server-side scheduler publishes,
archives, and marks guides review-due when configured dates pass.

Workflow endpoints are below `/api/v1/tenants/{tenant}/guides/{guide}/workflow`:

- `GET /` returns dates, reviewers, comments, and transition history.
- `POST /transitions` applies an authorised state transition.
- `POST /comments` adds a review comment.
- `POST /reviewers` assigns an active tenant user.

## Library resources

The catalogue supports databases, librarian profiles, FAQs, books, reading lists, and a
hierarchical subject taxonomy. Core fields are relational columns; extensible type-specific
attributes can use the validated `metadata` JSON object. PostgreSQL full-text vectors make all
published catalogue records searchable without making a search index the source of truth.

Resources may be linked to multiple subjects and guides. Reading lists contain ordered book
relationships, and librarian liaison assignments are represented through subject links.
Cross-tenant relationship IDs are rejected before persistence.

Authenticated management endpoints:

- `GET|POST /api/v1/tenants/{tenant}/resources`
- `GET|PUT /api/v1/tenants/{tenant}/resources/{id}`
- `GET|POST /api/v1/tenants/{tenant}/subjects`
- `PUT /api/v1/tenants/{tenant}/subjects/{id}`

Public discovery endpoints:

- `GET /api/v1/public/tenants/{tenant}/resources?type=&q=`
- `GET /api/v1/public/tenants/{tenant}/resources/{id}`
- `GET /api/v1/public/tenants/{tenant}/subjects`

Only `PUBLISHED` resources and active subjects are public. Management permissions are split
between `database.manage`, `staff.manage`, `faq.manage`, and `resource.manage`.

The administrative resource centre is `/admin/resources`; the public directory is `/resources`.
