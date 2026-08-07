-- GRACE Database Initialization Script
-- This script runs automatically on first database startup

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create enum types for various statuses
CREATE TYPE guide_status AS ENUM (
    'DRAFT',
    'IN_REVIEW',
    'APPROVED',
    'SCHEDULED',
    'PUBLISHED',
    'REVIEW_DUE',
    'ARCHIVED'
);

CREATE TYPE publication_status AS ENUM (
    'DRAFT',
    'PUBLISHED',
    'ARCHIVED'
);

CREATE TYPE access_type AS ENUM (
    'OPEN',
    'SUBSCRIPTION',
    'TRIAL',
    'EMBARGOED',
    'PURCHASED'
);

-- Tenants table (multi-tenancy support)
CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    domain VARCHAR(255),
    logo_url VARCHAR(500),
    primary_color VARCHAR(7),
    secondary_color VARCHAR(7),
    theme_config JSONB DEFAULT '{}',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenants_slug ON tenants(slug);
CREATE INDEX idx_tenants_domain ON tenants(domain);

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    keycloak_id VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) NOT NULL,
    given_name VARCHAR(100),
    family_name VARCHAR(100),
    display_name VARCHAR(255),
    avatar_url VARCHAR(500),
    bio TEXT,
    orcid VARCHAR(20),
    is_active BOOLEAN DEFAULT true,
    last_login TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_keycloak ON users(keycloak_id);

-- Roles table
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_system_role BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_roles_tenant ON roles(tenant_id);

-- Permissions table
CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Role-Permission mapping
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- User-Role mapping
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Subjects taxonomy
CREATE TABLE IF NOT EXISTS subjects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    pref_label VARCHAR(255) NOT NULL,
    alt_labels TEXT[],
    broader_term_id UUID REFERENCES subjects(id),
    definition TEXT,
    notation VARCHAR(50),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_subjects_tenant ON subjects(tenant_id);
CREATE INDEX idx_subjects_broader ON subjects(broader_term_id);

-- Related terms for subjects (many-to-many)
CREATE TABLE IF NOT EXISTS subject_related_terms (
    subject_id UUID REFERENCES subjects(id) ON DELETE CASCADE,
    related_term_id UUID REFERENCES subjects(id) ON DELETE CASCADE,
    PRIMARY KEY (subject_id, related_term_id)
);

-- Courses table
CREATE TABLE IF NOT EXISTS courses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    programme VARCHAR(255),
    faculty VARCHAR(255),
    department VARCHAR(255),
    level VARCHAR(50),
    credits INTEGER,
    instructors TEXT[],
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_courses_tenant ON courses(tenant_id);
CREATE INDEX idx_courses_code ON courses(code);

-- Guides table
CREATE TABLE IF NOT EXISTS guides (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    owner_id UUID REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(300) NOT NULL,
    subtitle VARCHAR(500),
    description TEXT,
    type VARCHAR(50) NOT NULL,
    status guide_status DEFAULT 'DRAFT',
    subjects UUID[] REFERENCES subjects(id),
    courses UUID[] REFERENCES courses(id),
    tags TEXT[],
    seo_title VARCHAR(255),
    seo_description VARCHAR(500),
    canonical_url VARCHAR(500),
    published_at TIMESTAMP WITH TIME ZONE,
    scheduled_publish_at TIMESTAMP WITH TIME ZONE,
    scheduled_archive_at TIMESTAMP WITH TIME ZONE,
    review_due_date TIMESTAMP WITH TIME ZONE,
    last_reviewed_at TIMESTAMP WITH TIME ZONE,
    version INTEGER DEFAULT 1,
    view_count INTEGER DEFAULT 0,
    health_score INTEGER DEFAULT 100,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_guide_slug_per_tenant UNIQUE (tenant_id, slug)
);

CREATE INDEX idx_guides_tenant ON guides(tenant_id);
CREATE INDEX idx_guides_owner ON guides(owner_id);
CREATE INDEX idx_guides_status ON guides(status);
CREATE INDEX idx_guides_type ON guides(type);
CREATE INDEX idx_guides_subjects ON guides USING GIN(subjects);
CREATE INDEX idx_guides_courses ON guides USING GIN(courses);

-- Guide pages
CREATE TABLE IF NOT EXISTS guide_pages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    guide_id UUID REFERENCES guides(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(300) NOT NULL,
    content JSONB NOT NULL, -- Structured JSON content with blocks
    sort_order INTEGER DEFAULT 0,
    is_published BOOLEAN DEFAULT false,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_page_slug_per_guide UNIQUE (guide_id, slug)
);

CREATE INDEX idx_guide_pages_guide ON guide_pages(guide_id);

-- Guide version history
CREATE TABLE IF NOT EXISTS guide_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    guide_id UUID REFERENCES guides(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    content_snapshot JSONB,
    change_summary TEXT,
    changed_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_guide_versions_guide ON guide_versions(guide_id);

-- Editorial workflow actions
CREATE TABLE IF NOT EXISTS guide_workflow_actions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    guide_id UUID REFERENCES guides(id) ON DELETE CASCADE,
    action_type VARCHAR(50) NOT NULL, -- SUBMITTED, APPROVED, REJECTED, REVISION_REQUESTED, PUBLISHED, ARCHIVED
    performed_by UUID REFERENCES users(id),
    comments TEXT,
    from_status guide_status,
    to_status guide_status,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_guide_workflow_guide ON guide_workflow_actions(guide_id);

-- Databases (Electronic Resources)
CREATE TABLE IF NOT EXISTS databases (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(300) NOT NULL,
    description TEXT,
    provider VARCHAR(255),
    publisher VARCHAR(255),
    url VARCHAR(500) NOT NULL,
    proxy_url VARCHAR(500),
    access_type access_type DEFAULT 'SUBSCRIPTION',
    authentication_method VARCHAR(100),
    subjects UUID[] REFERENCES subjects(id),
    content_types TEXT[],
    coverage_start DATE,
    coverage_end DATE,
    trial_start_date DATE,
    trial_end_date DATE,
    subscription_start_date DATE,
    subscription_end_date DATE,
    cancellation_policy TEXT,
    statistics_url VARCHAR(500),
    tutorial_url VARCHAR(500),
    is_popular BOOLEAN DEFAULT false,
    is_open_access BOOLEAN DEFAULT false,
    is_trial BOOLEAN DEFAULT false,
    view_count INTEGER DEFAULT 0,
    click_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_database_slug_per_tenant UNIQUE (tenant_id, slug)
);

CREATE INDEX idx_databases_tenant ON databases(tenant_id);
CREATE INDEX idx_databases_title ON databases(title);
CREATE INDEX idx_databases_provider ON databases(provider);
CREATE INDEX idx_databases_subjects ON databases USING GIN(subjects);
CREATE INDEX idx_databases_access_type ON databases(access_type);

-- Books
CREATE TABLE IF NOT EXISTS books (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    isbn VARCHAR(20),
    isbn13 VARCHAR(20),
    title VARCHAR(500) NOT NULL,
    subtitle VARCHAR(500),
    authors TEXT[],
    editors TEXT[],
    edition VARCHAR(50),
    publisher VARCHAR(255),
    publication_date DATE,
    language VARCHAR(10) DEFAULT 'en',
    pages INTEGER,
    format VARCHAR(50),
    call_number VARCHAR(100),
    location VARCHAR(255),
    cover_image_url VARCHAR(500),
    description TEXT,
    subjects UUID[] REFERENCES subjects(id),
    series_title VARCHAR(255),
    series_volume VARCHAR(50),
    doi VARCHAR(100),
    availability_status VARCHAR(50),
    copies_total INTEGER DEFAULT 1,
    copies_available INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_books_tenant ON books(tenant_id);
CREATE INDEX idx_books_isbn ON books(isbn);
CREATE INDEX idx_books_title ON books(title);
CREATE INDEX idx_books_authors ON books USING GIN(authors);
CREATE INDEX idx_books_subjects ON books USING GIN(subjects);

-- Reading lists
CREATE TABLE IF NOT EXISTS reading_lists (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    course_id UUID REFERENCES courses(id),
    guide_id UUID REFERENCES guides(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    academic_year VARCHAR(20),
    semester VARCHAR(50),
    is_published BOOLEAN DEFAULT false,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reading_lists_tenant ON reading_lists(tenant_id);
CREATE INDEX idx_reading_lists_course ON reading_lists(course_id);

-- Reading list items
CREATE TABLE IF NOT EXISTS reading_list_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reading_list_id UUID REFERENCES reading_lists(id) ON DELETE CASCADE,
    book_id UUID REFERENCES books(id),
    citation TEXT NOT NULL,
    item_type VARCHAR(50) NOT NULL, -- BOOK, CHAPTER, ARTICLE, RESOURCE
    importance VARCHAR(20) DEFAULT 'RECOMMENDED', -- REQUIRED, RECOMMENDED, FURTHER_READING
    week_number INTEGER,
    notes TEXT,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reading_items_list ON reading_list_items(reading_list_id);

-- Librarians directory
CREATE TABLE IF NOT EXISTS librarians (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    job_title VARCHAR(255),
    department VARCHAR(255),
    office_location VARCHAR(255),
    office_hours TEXT,
    phone VARCHAR(50),
    expertise TEXT[],
    liaison_subjects UUID[] REFERENCES subjects(id),
    liaison_departments TEXT[],
    is_liaison BOOLEAN DEFAULT false,
    profile_visibility VARCHAR(20) DEFAULT 'PUBLIC', -- PUBLIC, INTERNAL, PRIVATE
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_librarians_user ON librarians(user_id);
CREATE INDEX idx_librarians_tenant ON librarians(tenant_id);
CREATE INDEX idx_librarians_expertise ON librarians USING GIN(expertise);

-- FAQs
CREATE TABLE IF NOT EXISTS faqs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    question VARCHAR(500) NOT NULL,
    answer TEXT NOT NULL,
    category VARCHAR(100),
    tags TEXT[],
    related_guide_ids UUID[] REFERENCES guides(id),
    helpful_count INTEGER DEFAULT 0,
    not_helpful_count INTEGER DEFAULT 0,
    view_count INTEGER DEFAULT 0,
    is_published BOOLEAN DEFAULT false,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_faqs_tenant ON faqs(tenant_id);
CREATE INDEX idx_faqs_category ON faqs(category);
CREATE INDEX idx_faqs_tags ON faqs USING GIN(tags);

-- User feedback
CREATE TABLE IF NOT EXISTS feedback (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    feedback_type VARCHAR(50) NOT NULL, -- BROKEN_LINK, SUGGESTION, RESOURCE_REQUEST, CORRECTION, OTHER
    resource_type VARCHAR(50), -- GUIDE, DATABASE, BOOK, FAQ
    resource_id UUID,
    url VARCHAR(500),
    description TEXT NOT NULL,
    submitted_by VARCHAR(255),
    submitter_email VARCHAR(255),
    status VARCHAR(50) DEFAULT 'PENDING', -- PENDING, IN_PROGRESS, RESOLVED, REJECTED
    response TEXT,
    responded_by UUID REFERENCES users(id),
    responded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_feedback_tenant ON feedback(tenant_id);
CREATE INDEX idx_feedback_type ON feedback(feedback_type);
CREATE INDEX idx_feedback_status ON feedback(status);

-- Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id),
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    resource_type VARCHAR(50),
    resource_id UUID,
    is_read BOOLEAN DEFAULT false,
    read_at TIMESTAMP WITH TIME ZONE,
    sent_via_email BOOLEAN DEFAULT false,
    email_sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_tenant ON notifications(tenant_id);
CREATE INDEX idx_notifications_unread ON notifications(user_id, is_read) WHERE is_read = false;

-- Analytics events
CREATE TABLE IF NOT EXISTS analytics_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id UUID,
    user_id UUID REFERENCES users(id),
    session_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    referrer VARCHAR(500),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_analytics_tenant ON analytics_events(tenant_id);
CREATE INDEX idx_analytics_type ON analytics_events(event_type);
CREATE INDEX idx_analytics_created ON analytics_events(created_at);

-- AI configuration per tenant
CREATE TABLE IF NOT EXISTS ai_configurations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE UNIQUE,
    provider VARCHAR(50), -- OLLAMA, LLAMA_CPP, VLLM, LOCALAI, OPENAI_COMPATIBLE
    endpoint VARCHAR(500),
    api_key VARCHAR(500),
    model VARCHAR(100),
    max_tokens INTEGER DEFAULT 2048,
    temperature NUMERIC(3,2) DEFAULT 0.7,
    enabled BOOLEAN DEFAULT false,
    token_quota_daily INTEGER,
    token_quota_monthly INTEGER,
    privacy_mode BOOLEAN DEFAULT true,
    log_prompts BOOLEAN DEFAULT false,
    custom_instructions TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_config_tenant ON ai_configurations(tenant_id);

-- AI prompt templates
CREATE TABLE IF NOT EXISTS ai_prompt_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    template TEXT NOT NULL,
    system_prompt TEXT,
    variables TEXT[],
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_templates_tenant ON ai_prompt_templates(tenant_id);

-- Link checker results
CREATE TABLE IF NOT EXISTS link_check_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID NOT NULL,
    url VARCHAR(500) NOT NULL,
    status_code INTEGER,
    redirect_url VARCHAR(500),
    error_message TEXT,
    response_time_ms INTEGER,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_broken BOOLEAN DEFAULT false
);

CREATE INDEX idx_link_check_resource ON link_check_results(resource_type, resource_id);
CREATE INDEX idx_link_check_broken ON link_check_results(is_broken) WHERE is_broken = true;

-- Insert default permissions
INSERT INTO permissions (name, description, resource, action) VALUES
    ('guide.create', 'Create new guides', 'guide', 'create'),
    ('guide.read', 'View guides', 'guide', 'read'),
    ('guide.update', 'Edit existing guides', 'guide', 'update'),
    ('guide.delete', 'Delete guides', 'guide', 'delete'),
    ('guide.publish', 'Publish guides', 'guide', 'publish'),
    ('guide.review', 'Review guides', 'guide', 'review'),
    ('guide.approve', 'Approve guides for publication', 'guide', 'approve'),
    ('database.create', 'Create database entries', 'database', 'create'),
    ('database.read', 'View databases', 'database', 'read'),
    ('database.update', 'Edit database entries', 'database', 'update'),
    ('database.delete', 'Delete database entries', 'database', 'delete'),
    ('book.create', 'Create book entries', 'book', 'create'),
    ('book.read', 'View books', 'book', 'read'),
    ('book.update', 'Edit book entries', 'book', 'update'),
    ('book.delete', 'Delete book entries', 'book', 'delete'),
    ('faq.create', 'Create FAQs', 'faq', 'create'),
    ('faq.read', 'View FAQs', 'faq', 'read'),
    ('faq.update', 'Edit FAQs', 'faq', 'update'),
    ('faq.delete', 'Delete FAQs', 'faq', 'delete'),
    ('faq.manage', 'Manage FAQ categories', 'faq', 'manage'),
    ('analytics.view', 'View analytics dashboards', 'analytics', 'view'),
    ('ai.configure', 'Configure AI settings', 'ai', 'configure'),
    ('ai.use', 'Use AI features', 'ai', 'use'),
    ('user.manage', 'Manage users', 'user', 'manage'),
    ('role.manage', 'Manage roles and permissions', 'role', 'manage'),
    ('tenant.configure', 'Configure tenant settings', 'tenant', 'configure');

-- Insert default roles (will be customized per tenant)
INSERT INTO roles (name, description, is_system_role) VALUES
    ('SUPER_ADMIN', 'System-wide administrator', true),
    ('TENANT_ADMIN', 'Tenant-level administrator', true),
    ('LIBRARY_ADMIN', 'Library administrator', true),
    ('GUIDE_ADMIN', 'Guide management administrator', true),
    ('LIBRARIAN', 'Librarian with full content permissions', true),
    ('CONTRIBUTOR', 'Can create and edit own content', true),
    ('REVIEWER', 'Can review and approve content', true),
    ('FACULTY', 'Faculty member with elevated privileges', true),
    ('STUDENT', 'Student user', true),
    ('PUBLIC', 'Public/unauthenticated user', true);

-- Grant all permissions to SUPER_ADMIN role initially
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN';

COMMENT ON TABLE tenants IS 'Multi-tenant institution configurations';
COMMENT ON TABLE users IS 'User accounts linked to Keycloak identities';
COMMENT ON TABLE guides IS 'Library research and subject guides';
COMMENT ON TABLE guide_pages IS 'Pages within guides with structured JSON content';
COMMENT ON TABLE subjects IS 'Controlled vocabulary taxonomy (SKOS-compatible)';
COMMENT ON TABLE courses IS 'Academic courses for guide association';
COMMENT ON TABLE databases IS 'Electronic resources and databases A-Z';
COMMENT ON TABLE books IS 'Book catalog with ISBN and availability';
COMMENT ON TABLE reading_lists IS 'Course reading lists';
COMMENT ON TABLE librarians IS 'Librarian directory with expertise';
COMMENT ON TABLE faqs IS 'Frequently asked questions knowledge base';
COMMENT ON TABLE feedback IS 'User feedback and suggestions';
COMMENT ON TABLE ai_configurations IS 'AI provider configuration per tenant';
COMMENT ON TABLE analytics_events IS 'Usage analytics and tracking';
