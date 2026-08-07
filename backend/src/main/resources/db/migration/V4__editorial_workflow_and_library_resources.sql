ALTER TABLE subjects ADD COLUMN IF NOT EXISTS slug VARCHAR(300);
UPDATE subjects SET slug = lower(regexp_replace(trim(pref_label), '[^a-zA-Z0-9]+', '-', 'g')) || '-' || left(id::text, 8)
WHERE slug IS NULL;
ALTER TABLE subjects ALTER COLUMN slug SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS subjects_tenant_slug_unique ON subjects(tenant_id, slug);

ALTER TABLE guides ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE guides ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE guides ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE guides ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS guides_schedule_idx ON guides(status, scheduled_at, expires_at, next_review_at);

CREATE TABLE guide_reviewers (
    guide_id UUID NOT NULL REFERENCES guides(id) ON DELETE CASCADE,
    reviewer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (guide_id, reviewer_id)
);

CREATE TABLE guide_workflow_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    guide_id UUID NOT NULL REFERENCES guides(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(30) NOT NULL,
    to_status VARCHAR(30) NOT NULL,
    comment TEXT NOT NULL DEFAULT '',
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workflow_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);
CREATE INDEX guide_workflow_events_guide_idx ON guide_workflow_events(guide_id, created_at DESC);

CREATE TABLE guide_review_comments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    guide_id UUID NOT NULL REFERENCES guides(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    body TEXT NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT review_comment_not_blank CHECK (length(trim(body)) > 0)
);
CREATE INDEX guide_review_comments_guide_idx ON guide_review_comments(guide_id, created_at);

CREATE TABLE library_resources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    resource_type VARCHAR(30) NOT NULL,
    title VARCHAR(500) NOT NULL,
    slug VARCHAR(300) NOT NULL,
    alternative_title VARCHAR(500),
    description TEXT NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    provider VARCHAR(255),
    publisher VARCHAR(255),
    canonical_url VARCHAR(1500),
    proxy_mode VARCHAR(30) NOT NULL DEFAULT 'NONE',
    access_type VARCHAR(80),
    isbn VARCHAR(32),
    authors TEXT[] NOT NULL DEFAULT '{}',
    email VARCHAR(320),
    phone VARCHAR(80),
    orcid VARCHAR(40),
    faq_answer TEXT,
    featured BOOLEAN NOT NULL DEFAULT false,
    open_access BOOLEAN NOT NULL DEFAULT false,
    trial_ends_at DATE,
    helpful_votes INTEGER NOT NULL DEFAULT 0,
    unhelpful_votes INTEGER NOT NULL DEFAULT 0,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    updated_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(alternative_title, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(description, '')), 'C')
    ) STORED,
    CONSTRAINT library_resource_type_valid CHECK (resource_type IN ('DATABASE', 'LIBRARIAN', 'FAQ', 'BOOK', 'READING_LIST')),
    CONSTRAINT library_resource_status_valid CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT library_resource_proxy_valid CHECK (proxy_mode IN ('NONE', 'EZPROXY', 'OPENATHENS', 'SHIBBOLETH', 'INSTITUTIONAL')),
    CONSTRAINT library_resource_metadata_object CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT database_url_required CHECK (resource_type <> 'DATABASE' OR canonical_url IS NOT NULL),
    CONSTRAINT faq_answer_required CHECK (resource_type <> 'FAQ' OR faq_answer IS NOT NULL),
    CONSTRAINT library_resource_tenant_slug UNIQUE (tenant_id, slug)
);
CREATE INDEX library_resources_tenant_type_idx ON library_resources(tenant_id, resource_type, status) WHERE deleted_at IS NULL;
CREATE INDEX library_resources_search_idx ON library_resources USING GIN(search_vector);

CREATE TABLE resource_subjects (
    resource_id UUID NOT NULL REFERENCES library_resources(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    PRIMARY KEY (resource_id, subject_id)
);

CREATE TABLE guide_resources (
    guide_id UUID NOT NULL REFERENCES guides(id) ON DELETE CASCADE,
    resource_id UUID NOT NULL REFERENCES library_resources(id) ON DELETE CASCADE,
    relationship VARCHAR(40) NOT NULL DEFAULT 'RELATED',
    position INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guide_id, resource_id),
    CONSTRAINT guide_resource_position_positive CHECK (position >= 0)
);

CREATE TABLE reading_list_items (
    reading_list_id UUID NOT NULL REFERENCES library_resources(id) ON DELETE CASCADE,
    book_id UUID NOT NULL REFERENCES library_resources(id) ON DELETE CASCADE,
    reading_category VARCHAR(40) NOT NULL DEFAULT 'RECOMMENDED',
    section_title VARCHAR(255),
    week_number INTEGER,
    position INTEGER NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (reading_list_id, book_id),
    CONSTRAINT reading_list_position_positive CHECK (position >= 0),
    CONSTRAINT reading_list_week_positive CHECK (week_number IS NULL OR week_number > 0)
);

CREATE TABLE librarian_subject_liaisons (
    librarian_id UUID NOT NULL REFERENCES library_resources(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (librarian_id, subject_id)
);

INSERT INTO permissions (name, description, resource, action) VALUES
    ('resource.manage', 'Manage subjects, books, reading lists, and library resources', 'resource', 'manage')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'resource.manage'
WHERE r.name IN ('SUPER_ADMIN', 'TENANT_ADMIN', 'LIBRARY_ADMIN', 'LIBRARIAN')
ON CONFLICT DO NOTHING;
