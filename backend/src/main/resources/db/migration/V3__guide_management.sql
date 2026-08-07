CREATE TABLE guide_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    type_key VARCHAR(80) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    description VARCHAR(500) NOT NULL,
    system_type BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX guide_type_global_key ON guide_types(type_key) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX guide_type_tenant_key ON guide_types(tenant_id, type_key) WHERE tenant_id IS NOT NULL;

CREATE TABLE guide_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    template_key VARCHAR(100) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(500) NOT NULL,
    guide_type_id UUID NOT NULL REFERENCES guide_types(id),
    definition JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT guide_template_tenant_key UNIQUE (tenant_id, template_key),
    CONSTRAINT guide_template_definition_object CHECK (jsonb_typeof(definition) = 'object')
);

CREATE TABLE guides (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    guide_type_id UUID NOT NULL REFERENCES guide_types(id),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(300) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    tags TEXT[] NOT NULL DEFAULT '{}',
    metadata JSONB NOT NULL DEFAULT '{}',
    featured_image_url VARCHAR(1000),
    seo_title VARCHAR(255),
    seo_description VARCHAR(500),
    last_reviewed_at TIMESTAMP WITH TIME ZONE,
    next_review_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    revision_number INTEGER NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT guide_unique_tenant_slug UNIQUE (tenant_id, slug),
    CONSTRAINT guide_status_valid CHECK (status IN (
        'DRAFT', 'IN_REVIEW', 'APPROVED', 'SCHEDULED', 'PUBLISHED', 'REVIEW_DUE', 'ARCHIVED'
    )),
    CONSTRAINT guide_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);
CREATE INDEX guides_tenant_status_idx ON guides(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX guides_owner_idx ON guides(owner_id) WHERE deleted_at IS NULL;
CREATE INDEX guides_tags_idx ON guides USING GIN(tags);

CREATE TABLE guide_editors (
    guide_id UUID NOT NULL REFERENCES guides(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (guide_id, user_id)
);

CREATE TABLE guide_subjects (
    guide_id UUID NOT NULL REFERENCES guides(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    PRIMARY KEY (guide_id, subject_id)
);

CREATE TABLE guide_pages (
    id UUID PRIMARY KEY,
    guide_id UUID NOT NULL REFERENCES guides(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(300) NOT NULL,
    position INTEGER NOT NULL,
    layout JSONB NOT NULL DEFAULT '{"columns": 1}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT guide_page_unique_slug UNIQUE (guide_id, slug),
    CONSTRAINT guide_page_unique_position UNIQUE (guide_id, position),
    CONSTRAINT guide_page_position_positive CHECK (position >= 0),
    CONSTRAINT guide_page_layout_object CHECK (jsonb_typeof(layout) = 'object')
);

CREATE TABLE reusable_blocks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(180) NOT NULL,
    block_type VARCHAR(60) NOT NULL,
    content JSONB NOT NULL,
    settings JSONB NOT NULL DEFAULT '{}',
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT reusable_block_content_object CHECK (jsonb_typeof(content) = 'object'),
    CONSTRAINT reusable_block_settings_object CHECK (jsonb_typeof(settings) = 'object')
);
CREATE INDEX reusable_blocks_tenant_idx ON reusable_blocks(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE content_blocks (
    id UUID PRIMARY KEY,
    page_id UUID NOT NULL REFERENCES guide_pages(id) ON DELETE CASCADE,
    block_type VARCHAR(60) NOT NULL,
    position INTEGER NOT NULL,
    content JSONB NOT NULL,
    settings JSONB NOT NULL DEFAULT '{}',
    reusable_block_id UUID REFERENCES reusable_blocks(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT content_block_unique_position UNIQUE (page_id, position),
    CONSTRAINT content_block_position_positive CHECK (position >= 0),
    CONSTRAINT content_block_content_object CHECK (jsonb_typeof(content) = 'object'),
    CONSTRAINT content_block_settings_object CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT content_block_type_valid CHECK (block_type IN (
        'RICH_TEXT', 'HEADING', 'IMAGE', 'LINK_LIST', 'DATABASE_LIST', 'BOOK_LIST',
        'LIBRARIAN_PROFILE', 'CONTACT', 'SEARCH_BOX', 'FAQ', 'TABS', 'ACCORDION',
        'CARDS', 'ALERT', 'CALLOUT', 'TIMELINE', 'TABLE', 'CITATION', 'EMBED',
        'CODE', 'MAP', 'RSS_FEED', 'DATASET', 'POLL', 'FEEDBACK_FORM', 'QUIZ',
        'LEARNING_ACTIVITY', 'AI_ASSISTANT', 'RELATED_GUIDES', 'RECOMMENDED_READING',
        'SEMANTIC_KNOWLEDGE_PANEL', 'VIDEO', 'AUDIO', 'FILE', 'IMAGE_GALLERY'
    ))
);

CREATE TABLE guide_revisions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    guide_id UUID NOT NULL REFERENCES guides(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    change_summary VARCHAR(500) NOT NULL,
    snapshot JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT guide_revision_unique_number UNIQUE (guide_id, revision_number),
    CONSTRAINT guide_revision_snapshot_object CHECK (jsonb_typeof(snapshot) = 'object')
);
CREATE INDEX guide_revisions_guide_idx ON guide_revisions(guide_id, revision_number DESC);

INSERT INTO guide_types (type_key, display_name, description, system_type) VALUES
    ('SUBJECT', 'Subject Guide', 'Resources and research guidance for a subject area.', true),
    ('COURSE', 'Course Guide', 'Research support aligned with a course.', true),
    ('RESEARCH', 'Research Guide', 'Guidance for a research method or process.', true),
    ('TOPIC', 'Topic Guide', 'Resources focused on a defined topic.', true),
    ('PROGRAMME', 'Programme Guide', 'Support for an academic programme.', true),
    ('ASSIGNMENT', 'Assignment Guide', 'Resources for a specific assignment.', true),
    ('DATABASE', 'Database Guide', 'Help using a database or discovery service.', true),
    ('LIBRARY_SERVICE', 'Library Service Guide', 'Information about a library service.', true),
    ('INFORMATION_LITERACY', 'Information Literacy Guide', 'Teaching and learning content for information literacy.', true),
    ('OER', 'Open Educational Resources Guide', 'Discovery and use of open educational resources.', true),
    ('RESEARCH_DATA', 'Research Data Guide', 'Research data management guidance.', true),
    ('AI_LITERACY', 'AI Literacy Guide', 'Responsible and informed use of artificial intelligence.', true),
    ('SPECIAL_COLLECTIONS', 'Special Collections Guide', 'Curated access to archives and special collections.', true)
ON CONFLICT DO NOTHING;

INSERT INTO guide_templates (tenant_id, template_key, name, description, guide_type_id, definition)
SELECT t.id, seed.template_key, seed.name, seed.description, gt.id, seed.definition::jsonb
FROM tenants t
JOIN (VALUES
    ('subject-basic', 'Subject Guide', 'A concise subject guide with starting points and research help.', 'SUBJECT',
     '{"pages":[{"title":"Overview","slug":"overview","layout":{"columns":1},"blocks":[{"type":"HEADING","content":{"text":"Start your research","level":2},"settings":{}},{"type":"RICH_TEXT","content":{"text":"Introduce the subject, scope, and best starting points."},"settings":{}}]},{"title":"Key resources","slug":"key-resources","layout":{"columns":1},"blocks":[{"type":"DATABASE_LIST","content":{"title":"Recommended databases","items":[]},"settings":{}}]}]}'),
    ('course-basic', 'Course Guide', 'A course-focused guide with weekly resources and research support.', 'COURSE',
     '{"pages":[{"title":"Course resources","slug":"course-resources","layout":{"columns":1},"blocks":[{"type":"HEADING","content":{"text":"Course resources","level":2},"settings":{}},{"type":"RICH_TEXT","content":{"text":"Add course-specific databases, readings, and research guidance."},"settings":{}}]}]}'),
    ('research-basic', 'Research Guide', 'A research-process guide from question development to citation.', 'RESEARCH',
     '{"pages":[{"title":"Research process","slug":"research-process","layout":{"columns":1},"blocks":[{"type":"HEADING","content":{"text":"Plan your research","level":2},"settings":{}},{"type":"CALLOUT","content":{"title":"Research question","text":"Begin with a focused, answerable research question."},"settings":{"tone":"information"}}]}]}')
) AS seed(template_key, name, description, type_key, definition)
    ON true
JOIN guide_types gt ON gt.type_key = seed.type_key AND gt.tenant_id IS NULL
WHERE t.slug = 'default'
ON CONFLICT (tenant_id, template_key) DO NOTHING;
