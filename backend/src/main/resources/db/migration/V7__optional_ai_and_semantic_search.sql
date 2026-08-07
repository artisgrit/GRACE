CREATE TABLE ai_configurations (
    tenant_slug VARCHAR(100) PRIMARY KEY REFERENCES tenants(slug) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT false,
    provider_type VARCHAR(30) NOT NULL DEFAULT 'OLLAMA',
    base_url VARCHAR(1000) NOT NULL DEFAULT 'http://localhost:11434',
    chat_model VARCHAR(255) NOT NULL DEFAULT 'llama3.2',
    embedding_model VARCHAR(255) NOT NULL DEFAULT 'nomic-embed-text',
    api_key_env VARCHAR(120),
    allow_private_network BOOLEAN NOT NULL DEFAULT false,
    temperature NUMERIC(3,2) NOT NULL DEFAULT 0.20,
    max_tokens INTEGER NOT NULL DEFAULT 800,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    monthly_request_quota INTEGER,
    approved_sources TEXT[] NOT NULL DEFAULT '{GUIDE,DATABASE,BOOK,FAQ,LIBRARIAN,READING_LIST}',
    retain_prompts BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_provider_valid CHECK (provider_type IN ('OLLAMA','OPENAI_COMPATIBLE')),
    CONSTRAINT ai_temperature_range CHECK (temperature BETWEEN 0 AND 2),
    CONSTRAINT ai_max_tokens_range CHECK (max_tokens BETWEEN 64 AND 8192),
    CONSTRAINT ai_timeout_range CHECK (timeout_seconds BETWEEN 2 AND 180),
    CONSTRAINT ai_quota_positive CHECK (monthly_request_quota IS NULL OR monthly_request_quota > 0),
    CONSTRAINT ai_prompts_not_retained CHECK (retain_prompts = false)
);

INSERT INTO ai_configurations (tenant_slug)
SELECT slug FROM tenants ON CONFLICT (tenant_slug) DO NOTHING;

CREATE TABLE content_embeddings (
    tenant_slug VARCHAR(100) NOT NULL REFERENCES tenants(slug) ON DELETE CASCADE,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    title VARCHAR(500) NOT NULL,
    source_url VARCHAR(1500) NOT NULL,
    content_text TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding JSONB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_slug, source_type, source_id),
    CONSTRAINT embedding_json_array CHECK (jsonb_typeof(embedding) = 'array')
);
CREATE INDEX content_embeddings_tenant_idx ON content_embeddings(tenant_slug, source_type);

CREATE TABLE ai_usage_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_slug VARCHAR(100) NOT NULL REFERENCES tenants(slug) ON DELETE CASCADE,
    operation VARCHAR(30) NOT NULL,
    provider_type VARCHAR(30) NOT NULL,
    model VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_units INTEGER NOT NULL DEFAULT 0,
    output_units INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_usage_status_valid CHECK (status IN ('SUCCEEDED','FAILED','BLOCKED'))
);
CREATE INDEX ai_usage_tenant_month_idx ON ai_usage_events(tenant_slug, created_at DESC);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'ai.configure'
WHERE r.name IN ('SUPER_ADMIN','TENANT_ADMIN','LIBRARY_ADMIN')
ON CONFLICT DO NOTHING;
