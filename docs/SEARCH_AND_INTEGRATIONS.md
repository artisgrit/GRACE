# Search and Integration Operations

## Unified search

Published guides and library resources are rebuilt into the `grace-content-v1` OpenSearch index every 60 seconds. Each document carries a tenant key, content type, title, description, subjects, tags, body text, and public URL. The public API supports keyword relevance, type and subject filters, facets, pagination, and autocomplete.

When OpenSearch is unavailable, the same API searches published PostgreSQL records and marks the response with `degraded: true` and `source: postgresql`. Index runs are retained in `search_index_runs` and tenant administrators with `search.manage` can inspect status or request a rebuild.

Public endpoints:

- `GET /api/v1/public/tenants/{tenant}/search`
- `GET /api/v1/public/tenants/{tenant}/search/autocomplete`

Administrative endpoints:

- `GET /api/v1/tenants/{tenant}/search/status`
- `POST /api/v1/tenants/{tenant}/search/reindex`

## Integration runtime

The integration registry now has operational profiles for DSpace REST, OAI-PMH, VuFind, Koha REST, OJS REST, and LTI 1.3 registration validation. Configuration remains tenant-scoped. Connection tests and bounded synchronizations use short connect/read timeouts and always return a structured success or failure result.

Every operation is persisted in `integration_runs`; the latest structured result is also written to the connector health field. Remote HTTP errors, malformed payloads, timeouts, disabled connectors, and incomplete configuration are contained at the connector boundary and do not make the GRACE API unhealthy.

Administrative endpoints:

- `GET /api/v1/tenants/{tenant}/integrations/runs`
- `POST /api/v1/tenants/{tenant}/integrations/{connector}/test`
- `POST /api/v1/tenants/{tenant}/integrations/{connector}/sync`

LTI support in this phase validates platform registration (`issuer`, `clientId`, and `deploymentId`). Signed launch validation, deep linking, and grade services remain later implementation work.

## Configuration examples

```json
{"baseUrl":"https://repository.example.edu","metadataPrefix":"oai_dc"}
```

```json
{"issuer":"https://lms.example.edu","clientId":"grace","deploymentId":"production","jwksUrl":"https://lms.example.edu/.well-known/jwks.json"}
```

Do not place secrets in source-controlled configuration. Redirects are not followed and loopback, link-local, and private network destinations are blocked by default. An intentional intranet connector must set `allowPrivateNetwork` to `true`; production deployments should additionally restrict outbound network access to approved institutional systems.
