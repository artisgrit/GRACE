# Secured MCP Server

GRACE Phase 10 exposes published tenant research and discovery data through a read-only Model Context Protocol endpoint. The transport implements the stable MCP `2025-11-25` JSON-RPC contract over Streamable HTTP-style POST exchanges.

## Endpoint and Authentication

```text
POST   /api/v1/mcp/tenants/{tenant}
DELETE /api/v1/mcp/tenants/{tenant}
GET    /.well-known/oauth-protected-resource
```

Every MCP request requires a bearer token with:

- the `mcp.read` permission
- the `grace-mcp` audience
- a tenant claim matching the endpoint tenant, unless the user has global tenant authority

The server advertises OAuth Protected Resource Metadata. Configure the externally reachable authorization server with `GRACE_MCP_AUTHORIZATION_SERVER`; production authorization and resource endpoints must use HTTPS.

Initialize first. The server returns `Mcp-Session-Id`; send it with `MCP-Protocol-Version: 2025-11-25` on subsequent requests. Sessions expire after 30 minutes and are bound to the authenticated subject and tenant. Delete the endpoint with the session header to close early.

## Read-only Tools

| Tool | Purpose |
|---|---|
| `search_library` | Search published guides and resources with optional type/subject filters |
| `get_published_guide` | Read a published guide by slug |
| `get_library_resource` | Read a published resource by UUID |
| `list_subjects` | Read the active SKOS-aligned subject vocabulary |
| `get_semantic_graph` | Read the tenant's JSON-LD knowledge graph |

All tool schemas use JSON Schema 2020-12 and declare read-only, non-destructive annotations. Unsupported tools and invalid arguments return MCP tool-execution errors. No create, update, delete, workflow, configuration, or administrative operation is exposed.

## Resources

`resources/list` and `resources/read` expose:

```text
grace://{tenant}/guides
grace://{tenant}/subjects
grace://{tenant}/semantic/graph
```

Cross-tenant resource URIs are rejected.

## Security and Privacy

- Browser `Origin` values are validated against `GRACE_MCP_ALLOWED_ORIGINS`; non-browser clients may omit the header.
- Tokens are never forwarded to search providers, AI providers, or external integrations.
- MCP sessions are in-memory, bounded by time, and invalidated when the backend restarts.
- Audit rows contain actor, operation, target type, status, and timestamp only. Queries, tool arguments, results, and resource content are not logged.
- Only active subjects and published, non-deleted content can be returned.

The default local authorization server URL is suitable only for development. Configure Keycloak or another OAuth 2.1-compatible authorization server, HTTPS origins, token audience mapping, and the public base URL before production deployment.
