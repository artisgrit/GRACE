CREATE TABLE search_index_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_slug VARCHAR(100) NOT NULL REFERENCES tenants(slug) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    documents_indexed INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    CONSTRAINT search_index_status_valid CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))
);
CREATE INDEX search_index_runs_tenant_idx ON search_index_runs(tenant_slug, started_at DESC);

CREATE TABLE integration_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_slug VARCHAR(100) NOT NULL REFERENCES tenants(slug) ON DELETE CASCADE,
    connector_key VARCHAR(120) NOT NULL,
    operation VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    records_processed INTEGER NOT NULL DEFAULT 0,
    details JSONB NOT NULL DEFAULT '{}',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    CONSTRAINT integration_run_status_valid CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT integration_run_details_object CHECK (jsonb_typeof(details) = 'object')
);
CREATE INDEX integration_runs_tenant_idx ON integration_runs(tenant_slug, started_at DESC);

INSERT INTO permissions (name, description, resource, action) VALUES
    ('search.manage', 'Monitor and rebuild tenant search indexes', 'search', 'manage')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'search.manage'
WHERE r.name IN ('SUPER_ADMIN', 'TENANT_ADMIN', 'LIBRARY_ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO integration_configurations
    (tenant_slug, connector_key, connector_type, display_name, enabled,
     configuration_schema, configuration, mapping_profile, schedule, health)
SELECT 'default', seed.connector_key, seed.connector_type, seed.display_name, false,
       seed.schema::jsonb, '{}'::jsonb, '{}'::jsonb, '{"enabled":false}'::jsonb, '{"status":"not_configured"}'::jsonb
FROM (VALUES
    ('oai-pmh', 'REPOSITORY', 'OAI-PMH Harvester', '{"required":["baseUrl"],"properties":{"baseUrl":{"type":"string"},"metadataPrefix":{"type":"string","default":"oai_dc"}}}'),
    ('vufind', 'DISCOVERY', 'VuFind Discovery', '{"required":["baseUrl"],"properties":{"baseUrl":{"type":"string"}}}'),
    ('koha', 'ILS', 'Koha Library System', '{"required":["baseUrl"],"properties":{"baseUrl":{"type":"string"},"apiToken":{"type":"string","secret":true}}}'),
    ('ojs', 'REPOSITORY', 'Open Journal Systems', '{"required":["baseUrl"],"properties":{"baseUrl":{"type":"string"},"apiToken":{"type":"string","secret":true}}}'),
    ('lti', 'LMS', 'LTI 1.3 Platform', '{"required":["issuer","clientId","deploymentId"],"properties":{"issuer":{"type":"string"},"clientId":{"type":"string"},"deploymentId":{"type":"string"},"jwksUrl":{"type":"string"}}}')
) AS seed(connector_key, connector_type, display_name, schema)
ON CONFLICT (tenant_slug, connector_key) DO NOTHING;
