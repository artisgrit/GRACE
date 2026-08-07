# Identity, Tenancy and RBAC

GRACE Phase 1 provides PostgreSQL-backed tenants, local users, roles, granular permissions,
BCrypt password hashes, short-lived signed JWT access tokens, and tenant-bound authorization.

## Local development sign-in

Docker Compose creates the initial administrator only when the configured email does not
already exist. The development defaults are:

- Institution: `default`
- Email: `admin@grace.local`
- Password: `ChangeThisAdminPassword!`

Set `GRACE_BOOTSTRAP_ADMIN_EMAIL` and `GRACE_BOOTSTRAP_ADMIN_PASSWORD` in `.env` before the
first production start. Remove both bootstrap variables after the administrator exists. Never
use the development defaults outside a local environment.

The browser posts credentials to the Next.js server. Next.js stores the backend access token
in an HTTP-only, SameSite=Lax cookie and proxies authenticated calls through `/api/grace/**`.
Set `COOKIE_SECURE=true` behind production HTTPS.

## API

- `POST /api/v1/auth/login` authenticates a local account.
- `GET /api/v1/auth/me` returns the current persisted user and effective permissions.
- `GET|POST /api/v1/tenants` lists or creates tenants for global administrators.
- `GET|POST /api/v1/tenants/{tenant}/users` manages users.
- `PUT /api/v1/tenants/{tenant}/users/{id}/roles` changes role assignments.
- `GET /api/v1/tenants/{tenant}/roles` lists roles and effective permissions.
- `PUT /api/v1/tenants/{tenant}/roles/{id}/permissions` changes role permissions.

Configuration, identity-provider, integration, and audit APIs now require their corresponding
permissions. Every tenant-scoped request compares the path tenant with the signed token claim;
only `tenant.manage.global` permits cross-tenant administration.

## Migration

`V2__identity_tenancy_and_rbac.sql` evolves the legacy schema in place. It retains existing
tenant, user, role, and permission IDs so foreign keys from guides and other library domains
remain valid. Existing external-only users receive a non-matching password marker and must use
their identity provider or have a local password set through a future password-reset workflow.

## Current security boundary

Local sign-in and authorization are operational. Password reset, refresh-token rotation,
account lockout, MFA, OIDC/SAML assertion validation, and secret encryption remain required
before a production release. Keycloak remains deployed as the recommended external identity
provider, but its login flow is not yet connected to this local token service.
