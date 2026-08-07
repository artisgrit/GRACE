CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    domain VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS primary_domain VARCHAR(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS timezone VARCHAR(80) NOT NULL DEFAULT 'UTC';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS default_locale VARCHAR(20) NOT NULL DEFAULT 'en';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
UPDATE tenants SET primary_domain = domain WHERE primary_domain IS NULL;
UPDATE tenants SET enabled = COALESCE(is_active, true);

CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(120) UNIQUE NOT NULL,
    description TEXT NOT NULL,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_system_role BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS display_name VARCHAR(160);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS system_role BOOLEAN NOT NULL DEFAULT false;
UPDATE roles SET display_name = COALESCE(display_name, INITCAP(REPLACE(name, '_', ' ')));
UPDATE roles SET system_role = COALESCE(is_system_role, false);
ALTER TABLE roles ALTER COLUMN display_name SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS role_unique_tenant_name ON roles(tenant_id, name) WHERE tenant_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    keycloak_id VARCHAR(255) UNIQUE,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    last_login TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE users ALTER COLUMN keycloak_id DROP NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
UPDATE users SET password_hash = '!external-only' WHERE password_hash IS NULL;
UPDATE users SET enabled = COALESCE(is_active, true), last_login_at = COALESCE(last_login_at, last_login);
UPDATE users SET display_name = COALESCE(display_name, email);
ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS users_unique_tenant_email
    ON users(tenant_id, LOWER(email)) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS users_active_tenant_idx ON users(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

INSERT INTO tenants (slug, name, timezone, default_locale, enabled)
VALUES ('default', 'GRACE Library', 'Africa/Kampala', 'en', true)
ON CONFLICT (slug) DO UPDATE SET timezone = EXCLUDED.timezone, enabled = true;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'appearance_tenant_fk') THEN
        ALTER TABLE appearance_configurations ADD CONSTRAINT appearance_tenant_fk
            FOREIGN KEY (tenant_slug) REFERENCES tenants(slug) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'identity_provider_tenant_fk') THEN
        ALTER TABLE identity_provider_configurations ADD CONSTRAINT identity_provider_tenant_fk
            FOREIGN KEY (tenant_slug) REFERENCES tenants(slug) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'integration_tenant_fk') THEN
        ALTER TABLE integration_configurations ADD CONSTRAINT integration_tenant_fk
            FOREIGN KEY (tenant_slug) REFERENCES tenants(slug) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'configuration_audit_tenant_fk') THEN
        ALTER TABLE configuration_audit_events ADD CONSTRAINT configuration_audit_tenant_fk
            FOREIGN KEY (tenant_slug) REFERENCES tenants(slug) ON DELETE CASCADE;
    END IF;
END $$;

INSERT INTO permissions (name, description, resource, action) VALUES
    ('tenant.manage', 'Manage settings for the current tenant', 'tenant', 'manage'),
    ('tenant.manage.global', 'Create and manage every tenant', 'tenant', 'manage_global'),
    ('user.manage', 'Create and manage tenant users', 'user', 'manage'),
    ('role.manage', 'Assign roles and permissions', 'role', 'manage'),
    ('appearance.manage', 'Manage institutional appearance', 'appearance', 'manage'),
    ('integration.manage', 'Manage library integrations', 'integration', 'manage'),
    ('identity.manage', 'Manage identity providers', 'identity', 'manage'),
    ('security.audit.view', 'View security and configuration audit events', 'security_audit', 'view'),
    ('guide.create', 'Create guides', 'guide', 'create'),
    ('guide.edit', 'Edit guides', 'guide', 'edit'),
    ('guide.review', 'Review guide submissions', 'guide', 'review'),
    ('guide.publish', 'Publish approved guides', 'guide', 'publish'),
    ('guide.archive', 'Archive guides', 'guide', 'archive'),
    ('database.manage', 'Manage electronic resources', 'database', 'manage'),
    ('staff.manage', 'Manage librarian profiles', 'staff', 'manage'),
    ('faq.manage', 'Manage FAQs', 'faq', 'manage'),
    ('analytics.view', 'View analytics', 'analytics', 'view'),
    ('ai.configure', 'Configure optional AI features', 'ai', 'configure')
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (tenant_id, name, description, display_name, is_system_role, system_role)
SELECT t.id, seed.name, seed.display_name, seed.display_name, true, true
FROM tenants t
CROSS JOIN (VALUES
    ('SUPER_ADMIN', 'Super administrator'),
    ('TENANT_ADMIN', 'Tenant administrator'),
    ('LIBRARY_ADMIN', 'Library administrator'),
    ('GUIDE_ADMIN', 'Guide administrator'),
    ('LIBRARIAN', 'Librarian'),
    ('CONTRIBUTOR', 'Contributor'),
    ('REVIEWER', 'Reviewer'),
    ('FACULTY', 'Faculty'),
    ('STUDENT', 'Student'),
    ('AUTHENTICATED_USER', 'Authenticated user')
) AS seed(name, display_name)
WHERE t.slug = 'default'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
JOIN tenants t ON t.id = r.tenant_id
WHERE t.slug = 'default' AND r.name = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN tenants t ON t.id = r.tenant_id
JOIN permissions p ON p.name <> 'tenant.manage.global'
WHERE t.slug = 'default' AND r.name = 'TENANT_ADMIN' ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN tenants t ON t.id = r.tenant_id
JOIN permissions p ON p.name IN ('user.manage', 'role.manage', 'appearance.manage', 'integration.manage',
    'identity.manage', 'security.audit.view', 'guide.create', 'guide.edit', 'guide.review', 'guide.publish',
    'guide.archive', 'database.manage', 'staff.manage', 'faq.manage', 'analytics.view')
WHERE t.slug = 'default' AND r.name = 'LIBRARY_ADMIN' ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN tenants t ON t.id = r.tenant_id
JOIN permissions p ON p.name IN ('guide.create', 'guide.edit', 'guide.review', 'guide.publish', 'guide.archive')
WHERE t.slug = 'default' AND r.name = 'GUIDE_ADMIN' ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN tenants t ON t.id = r.tenant_id
JOIN permissions p ON p.name IN ('guide.create', 'guide.edit', 'database.manage', 'staff.manage', 'faq.manage')
WHERE t.slug = 'default' AND r.name = 'LIBRARIAN' ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN tenants t ON t.id = r.tenant_id
JOIN permissions p ON p.name IN ('guide.create', 'guide.edit')
WHERE t.slug = 'default' AND r.name = 'CONTRIBUTOR' ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN tenants t ON t.id = r.tenant_id
JOIN permissions p ON p.name = 'guide.review'
WHERE t.slug = 'default' AND r.name = 'REVIEWER' ON CONFLICT DO NOTHING;
