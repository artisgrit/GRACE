CREATE TABLE IF NOT EXISTS appearance_configurations (
    tenant_slug VARCHAR(100) PRIMARY KEY,
    schema_version INTEGER NOT NULL DEFAULT 1,
    active_theme_id VARCHAR(100) NOT NULL,
    brand JSONB NOT NULL,
    tokens JSONB NOT NULL,
    layout JSONB NOT NULL,
    navigation JSONB NOT NULL,
    components JSONB NOT NULL DEFAULT '[]'::jsonb,
    custom_css TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS identity_provider_configurations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_slug VARCHAR(100) NOT NULL,
    provider_key VARCHAR(120) NOT NULL,
    provider_type VARCHAR(40) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT false,
    default_provider BOOLEAN NOT NULL DEFAULT false,
    discovery_rules JSONB NOT NULL DEFAULT '{}'::jsonb,
    attribute_mappings JSONB NOT NULL DEFAULT '{}'::jsonb,
    role_mappings JSONB NOT NULL DEFAULT '{}'::jsonb,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT identity_provider_unique_key UNIQUE (tenant_slug, provider_key)
);

CREATE INDEX IF NOT EXISTS idx_identity_provider_tenant
    ON identity_provider_configurations(tenant_slug);

CREATE TABLE IF NOT EXISTS integration_configurations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_slug VARCHAR(100) NOT NULL,
    connector_key VARCHAR(120) NOT NULL,
    connector_type VARCHAR(60) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT false,
    configuration_schema JSONB NOT NULL,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    mapping_profile JSONB NOT NULL DEFAULT '{}'::jsonb,
    schedule JSONB NOT NULL DEFAULT '{}'::jsonb,
    health JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT integration_unique_key UNIQUE (tenant_slug, connector_key)
);

CREATE INDEX IF NOT EXISTS idx_integration_tenant
    ON integration_configurations(tenant_slug);

CREATE TABLE IF NOT EXISTS configuration_audit_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_slug VARCHAR(100) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    area VARCHAR(80) NOT NULL,
    action VARCHAR(80) NOT NULL,
    summary TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO appearance_configurations (
    tenant_slug,
    active_theme_id,
    brand,
    tokens,
    layout,
    navigation,
    components
) VALUES (
    'default',
    'default-institutional',
    '{
      "institutionName": "GRACE Library",
      "shortName": "GRACE",
      "logo": {"url": "", "alt": "GRACE Library"},
      "logoPlacement": "header-left",
      "favicon": "",
      "footerLogo": {"url": "", "alt": ""}
    }'::jsonb,
    '{
      "colorPrimary": "#0f766e",
      "colorSecondary": "#1d4ed8",
      "colorAccent": "#b45309",
      "background": "#f6f7f9",
      "surface": "#ffffff",
      "text": "#172033",
      "mutedText": "#5d6878",
      "border": "#d9dee7",
      "fontHeading": "Inter, ui-sans-serif, system-ui, sans-serif",
      "fontBody": "Inter, ui-sans-serif, system-ui, sans-serif",
      "radius": "8px",
      "containerWidth": "1120px"
    }'::jsonb,
    '{
      "preset": "standard",
      "headerRows": ["utility", "main", "primary-navigation"],
      "contentMode": "contained",
      "sidebar": {"enabled": false, "position": "left", "width": "280px"},
      "footerColumns": 3,
      "mobileNavigation": "drawer"
    }'::jsonb,
    '{
      "header": [
        {"label": "Guides", "href": "/guides"},
        {"label": "Databases", "href": "/databases"},
        {"label": "Research Support", "href": "/research-support"},
        {"label": "FAQs", "href": "/faqs"}
      ],
      "footer": [
        {"label": "Accessibility", "href": "/accessibility"},
        {"label": "Privacy", "href": "/privacy"},
        {"label": "Contact", "href": "/contact"}
      ]
    }'::jsonb,
    '[
      {"id": "hero", "type": "hero", "enabled": true, "order": 1},
      {"id": "quick-links", "type": "quickLinks", "enabled": true, "order": 2},
      {"id": "search", "type": "search", "enabled": true, "order": 3}
    ]'::jsonb
) ON CONFLICT (tenant_slug) DO NOTHING;

INSERT INTO identity_provider_configurations (
    tenant_slug,
    provider_key,
    provider_type,
    display_name,
    enabled,
    default_provider,
    attribute_mappings,
    role_mappings,
    configuration
) VALUES (
    'default',
    'keycloak',
    'OIDC',
    'Institutional SSO',
    true,
    true,
    '{"subject": "sub", "email": "email", "displayName": "name", "affiliation": "eduPersonAffiliation"}'::jsonb,
    '{"LIBRARIAN": ["librarian", "urn:grace:librarian"], "FACULTY": ["faculty"], "STUDENT": ["student"]}'::jsonb,
    '{"issuer": "http://localhost:8180/realms/grace", "clientId": "grace-frontend", "scopes": ["openid", "profile", "email"]}'::jsonb
) ON CONFLICT (tenant_slug, provider_key) DO NOTHING;

INSERT INTO integration_configurations (
    tenant_slug,
    connector_key,
    connector_type,
    display_name,
    enabled,
    configuration_schema,
    configuration,
    mapping_profile,
    schedule,
    health
) VALUES (
    'default',
    'dspace',
    'REPOSITORY',
    'DSpace Repository',
    false,
    '{"required": ["baseUrl"], "properties": {"baseUrl": {"type": "string"}, "oaiEndpoint": {"type": "string"}, "metadataPrefix": {"type": "string", "default": "oai_dc"}}}'::jsonb,
    '{}'::jsonb,
    '{"dc.title": "title", "dc.creator": "creators", "dc.subject": "subjects", "dc.identifier.uri": "sourceUrl"}'::jsonb,
    '{"mode": "manual"}'::jsonb,
    '{"enabled": false, "status": "not_configured", "recordsProcessed": 0, "details": {"message": "Connector is ready for configuration."}}'::jsonb
), (
    'default',
    'koha',
    'ILS',
    'Koha Catalogue',
    false,
    '{"required": ["baseUrl"], "properties": {"baseUrl": {"type": "string"}, "sruUrl": {"type": "string"}, "z3950Host": {"type": "string"}}}'::jsonb,
    '{}'::jsonb,
    '{"marc.245.a": "title", "marc.100.a": "author", "marc.020.a": "isbn", "marc.650.a": "subjects"}'::jsonb,
    '{"mode": "manual"}'::jsonb,
    '{"enabled": false, "status": "not_configured", "recordsProcessed": 0, "details": {"message": "Connector is ready for configuration."}}'::jsonb
) ON CONFLICT (tenant_slug, connector_key) DO NOTHING;
