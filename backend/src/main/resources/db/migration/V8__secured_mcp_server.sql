INSERT INTO permissions (name, description, resource, action) VALUES
    ('mcp.read', 'Use secured read-only MCP research and discovery tools', 'mcp', 'read')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name='mcp.read'
WHERE r.name IN (
    'SUPER_ADMIN','TENANT_ADMIN','LIBRARY_ADMIN','GUIDE_ADMIN','LIBRARIAN',
    'CONTRIBUTOR','REVIEWER','FACULTY','STUDENT','AUTHENTICATED_USER'
)
ON CONFLICT DO NOTHING;

CREATE TABLE mcp_access_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_slug VARCHAR(100) NOT NULL REFERENCES tenants(slug) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation VARCHAR(80) NOT NULL,
    target_type VARCHAR(80),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT mcp_event_status_valid CHECK (status IN ('SUCCEEDED','REJECTED','FAILED'))
);
CREATE INDEX mcp_access_events_tenant_time_idx ON mcp_access_events(tenant_slug, created_at DESC);
