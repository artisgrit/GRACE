CREATE TABLE analytics_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_slug VARCHAR(100) NOT NULL REFERENCES tenants(slug) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    entity_type VARCHAR(40),
    entity_id UUID,
    path VARCHAR(1000),
    session_hash VARCHAR(64),
    metadata JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT analytics_event_type_valid CHECK (event_type IN ('PAGE_VIEW','SEARCH','RESOURCE_CLICK','GUIDE_VIEW','FEEDBACK')),
    CONSTRAINT analytics_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);
CREATE INDEX analytics_events_tenant_time_idx ON analytics_events(tenant_slug, occurred_at DESC);
CREATE INDEX analytics_events_tenant_type_idx ON analytics_events(tenant_slug, event_type, occurred_at DESC);

CREATE TABLE link_check_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_slug VARCHAR(100) NOT NULL REFERENCES tenants(slug) ON DELETE CASCADE,
    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID NOT NULL,
    url VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    http_status INTEGER,
    error_message VARCHAR(1000),
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT link_check_status_valid CHECK (status IN ('HEALTHY','BROKEN','BLOCKED'))
);
CREATE INDEX link_check_latest_idx ON link_check_results(tenant_slug, entity_type, entity_id, checked_at DESC);

CREATE TABLE content_quality_assessments (
    guide_id UUID PRIMARY KEY REFERENCES guides(id) ON DELETE CASCADE,
    tenant_slug VARCHAR(100) NOT NULL REFERENCES tenants(slug) ON DELETE CASCADE,
    health_score INTEGER NOT NULL,
    accessibility_issues INTEGER NOT NULL DEFAULT 0,
    broken_links INTEGER NOT NULL DEFAULT 0,
    review_overdue BOOLEAN NOT NULL DEFAULT false,
    issues JSONB NOT NULL DEFAULT '[]',
    assessed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT health_score_range CHECK (health_score BETWEEN 0 AND 100),
    CONSTRAINT quality_issues_array CHECK (jsonb_typeof(issues) = 'array')
);
CREATE INDEX content_quality_tenant_score_idx ON content_quality_assessments(tenant_slug, health_score);

CREATE TABLE user_notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_slug VARCHAR(100) NOT NULL REFERENCES tenants(slug) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notification_type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    entity_type VARCHAR(40),
    entity_id UUID,
    deduplication_key VARCHAR(255) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notification_tenant_dedup UNIQUE (tenant_slug, deduplication_key)
);
CREATE INDEX notifications_recipient_idx ON user_notifications(recipient_id, read_at, created_at DESC);

INSERT INTO permissions (name, description, resource, action) VALUES
    ('quality.manage', 'Run link, review, and accessibility quality checks', 'quality', 'manage')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'quality.manage'
WHERE r.name IN ('SUPER_ADMIN','TENANT_ADMIN','LIBRARY_ADMIN','GUIDE_ADMIN')
ON CONFLICT DO NOTHING;
