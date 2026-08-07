# GRACE Configuration Foundation

This phase introduces persistent foundations for configuration-driven institutional design, federated identity setup, and interoperability administration.

## Appearance Configuration

The backend exposes:

- `GET /api/v1/tenants/{tenantSlug}/appearance`
- `PUT /api/v1/tenants/{tenantSlug}/appearance`

Appearance configuration is stored as structured JSONB:

- brand settings
- design tokens
- layout configuration
- navigation configuration
- component ordering
- tenant-scoped custom CSS

The frontend administration console is available at:

```text
http://localhost:3000/admin
```

It currently supports editing institutional name, logo placement, colour tokens, layout JSON, and navigation JSON.

## Identity Provider Configuration

The backend exposes:

- `GET /api/v1/tenants/{tenantSlug}/identity-providers`
- `PUT /api/v1/tenants/{tenantSlug}/identity-providers`

Provider records support:

- provider type, such as `OIDC`, `SAML`, `LDAP`, `CAS`, or `SHIBBOLETH_PROXY`
- display name
- enabled/default flags
- discovery rules
- attribute mappings
- role mappings
- provider configuration

This is the administration/configuration layer. Actual SAML/OIDC assertion and token validation must be implemented with maintained security libraries before any provider is treated as production-ready authentication.

## Integration Configuration

The backend exposes:

- `GET /api/v1/integration-catalogue`
- `GET /api/v1/tenants/{tenantSlug}/integrations`
- `PUT /api/v1/tenants/{tenantSlug}/integrations`

Connector records support:

- connector type
- configuration schema
- tenant configuration
- metadata mapping profile
- schedule configuration
- health metadata

Seeded connectors include DSpace and Koha.

## Audit

Configuration changes create audit events.

Endpoint:

```text
GET /api/v1/tenants/{tenantSlug}/audit-events
```

## Database

The schema is defined in:

```text
backend/src/main/resources/db/migration/V1__configuration_foundations.sql
```

The migration is idempotent and is executed during backend startup by `SchemaInitializer`.
