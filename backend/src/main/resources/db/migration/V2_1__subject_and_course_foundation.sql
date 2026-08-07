CREATE TABLE IF NOT EXISTS subjects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    pref_label VARCHAR(255) NOT NULL,
    alt_labels TEXT[] NOT NULL DEFAULT '{}',
    broader_term_id UUID REFERENCES subjects(id) ON DELETE SET NULL,
    definition TEXT NOT NULL DEFAULT '',
    notation VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subjects_tenant ON subjects(tenant_id);
CREATE INDEX IF NOT EXISTS idx_subjects_broader ON subjects(broader_term_id);

CREATE TABLE IF NOT EXISTS subject_related_terms (
    subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    related_term_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    PRIMARY KEY (subject_id, related_term_id),
    CONSTRAINT subject_not_related_to_self CHECK (subject_id <> related_term_id)
);

CREATE TABLE IF NOT EXISTS courses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    programme VARCHAR(255),
    faculty VARCHAR(255),
    department VARCHAR(255),
    level VARCHAR(50),
    credits INTEGER,
    instructors TEXT[] NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_courses_tenant ON courses(tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS courses_tenant_code_unique ON courses(tenant_id, code);
