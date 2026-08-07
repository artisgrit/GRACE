# Analytics and Content Quality

Phase 7 adds tenant-isolated operational analytics and scheduled content-quality maintenance.

## Analytics

Public pages record a limited event vocabulary: page views, guide views, searches, resource clicks, and feedback. GRACE does not collect IP addresses or user-agent strings in the analytics table, and the default search event does not retain the query text. Administrators with `analytics.view` receive aggregate totals, daily trends, and popular paths.

## Quality checks

The quality scheduler and manual admin action:

- check published resource and guide links with bounded HTTP timeouts;
- block loopback, link-local, and private-network targets to reduce SSRF exposure;
- do not follow redirects;
- identify images without alternative text;
- identify missing guide descriptions and overdue reviews;
- calculate a transparent 0-100 guide health score;
- create one review reminder per guide per day;
- retain link-check history for 90 days.

External failures are stored as link results and do not make GRACE unhealthy. The scheduled task isolates failures by tenant.

## APIs

- `POST /api/v1/public/tenants/{tenant}/analytics/events`
- `GET /api/v1/tenants/{tenant}/analytics/summary?days=30`
- `POST /api/v1/tenants/{tenant}/quality/run`
- `GET /api/v1/tenants/{tenant}/quality/guides`
- `GET /api/v1/tenants/{tenant}/notifications`

The administration workspace is available at `/admin/quality`.
