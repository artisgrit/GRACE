# GRACE (Guided Research, Access, Collaboration and Education)

A fully functional, production-ready, open-source Library Guides and Research Support Platform for universities, research institutions, colleges, archives, museums, and library consortia.

## Overview

GRACE extends beyond systems such as LibGuides and SubjectsPlus by integrating:

- **Library Guides** - Subject, Course, Research, Database, and Information Literacy guides
- **Research Support** - RDM, Open Science, Academic Integrity
- **Electronic Resource Management** - A-Z databases, books, reading lists
- **Information Literacy** - AI literacy, cybersecurity awareness
- **Knowledge Management** - FAQs, knowledge base, librarian directory
- **AI-assisted Discovery** - Optional AI gateway with local support
- **Retrieval-Augmented Generation (RAG)** - Cited answers from trusted sources
- **Semantic Search** - Hybrid lexical + vector search
- **Linked Data** - RDF, JSON-LD, SKOS, Schema.org
- **Knowledge Graphs** - Relationships between guides, subjects, courses, librarians
- **MCP (Model Context Protocol)** - Agent-based interoperability
- **Library Systems Integration** - DSpace, Koha, VuFind, OJS, LMS (LTI)
- **API-first Architecture** - Versioned REST APIs with OpenAPI documentation

## Key Features

### Multi-Tenancy
- Institution-independent design
- Tenant isolation with custom branding, domains, and configurations
- Suitable for worldwide adoption

### Authentication & RBAC
- Local accounts, OAuth2, OpenID Connect, SAML, LDAP, Active Directory
- Keycloak integration
- Role-based access control with fine-grained permissions

### Guide Management
- Drag-and-drop page builder with reusable blocks
- Editorial workflow: Draft → Review → Approved → Published → Archived
- Templates for various guide types
- Version history and scheduling

### Electronic Resources
- Complete A-Z database management
- Books and reading lists with ISBN, availability, citations
- Librarian directory with expertise and office hours
- FAQ and knowledge base with AI retrieval

### Search
- Unified search across all content types
- Semantic search with embeddings and vector indexing
- Faceted navigation and relevance ranking

### AI Integration (Optional)
- Provider abstraction (Ollama, llama.cpp, vLLM, LocalAI, OpenAI-compatible)
- RAG pipeline with chunking, embeddings, retrieval, and cited answers
- AI governance with quotas, limits, and privacy controls
- Human librarian control maintained

### Accessibility
- WCAG 2.2 AA compliant
- Keyboard navigation, screen reader support
- RTL language support
- Low-bandwidth mode

### Internationalisation
- English, French, Kiswahili, Portuguese, Arabic
- RTL language support

## Technology Stack

### Frontend
- Next.js, React, TypeScript
- Tailwind CSS
- React Hook Form, TanStack Query, Zustand

### Backend
- Java, Spring Boot, Spring Security
- Spring Data JPA, Hibernate, Flyway
- OpenAPI, Spring Actuator

### Infrastructure
- PostgreSQL (Database)
- OpenSearch (Search Engine)
- Redis (Cache)
- MinIO/S3 (Storage)
- Keycloak (Identity Provider)

## Repository Structure

```
grace/
├── frontend/          # Next.js application
├── backend/           # Spring Boot application
├── integrations/      # Library system integrations
├── semantic/          # Semantic web and linked data
├── mcp/               # Model Context Protocol server
├── deploy/            # Docker, Kubernetes, deployment configs
├── docs/              # Documentation
└── scripts/           # Build and deployment scripts
```

## Development Phases

| Phase | Focus Areas |
|-------|-------------|
| 0 | Architecture, Infrastructure, CI/CD, Docker, Authentication, RBAC |
| 1 | Identity, Tenancy, Authentication, RBAC |
| 2 | Guide Management, Pages, Blocks, Templates, Revisions |
| 3 | Editorial Workflow, Review, Publishing, Scheduling |
| 4 | Subjects, Databases, Librarians, FAQs, Books, Reading Lists |
| 5 | OpenSearch indexing, unified search, facets, autocomplete, resilient fallback |
| 6 | DSpace, OAI-PMH, VuFind, Koha, OJS and LTI foundations |
| 7 | Analytics, link checking, guide health, review reminders, accessibility auditing |
| 8 | Optional AI gateway, local models, embeddings, hybrid search, RAG assistant |

## Quick Start

```bash
# Clone the repository
git clone https://github.com/artisgrit/GRACE.git
cd grace

# Start all services with Docker Compose
docker-compose up -d

# Access the application
# Frontend: http://localhost:3000
# Administrator sign-in: http://localhost:3000/login
# Backend API: http://localhost:8080
# Keycloak: http://localhost:8180
# OpenSearch: http://localhost:9200
```

## Documentation

See the [docs/](docs/) directory for comprehensive documentation:

- [Identity, Tenancy and RBAC](docs/IDENTITY_TENANCY_RBAC.md)
- [Guide Management](docs/GUIDE_MANAGEMENT.md)
- [Editorial Workflow and Library Resources](docs/EDITORIAL_WORKFLOW_AND_RESOURCES.md)
- [Search and Integration Operations](docs/SEARCH_AND_INTEGRATIONS.md)
- [Analytics and Content Quality](docs/ANALYTICS_AND_CONTENT_QUALITY.md)
- [AI and Semantic Search](docs/AI_AND_SEMANTIC_SEARCH.md)

- Installation Guide
- Administration Guide
- Librarian Guide
- User Guide
- Developer Guide
- API Documentation
- AI Guide
- Semantic Web Guide
- Deployment Guide
- Security Guide
- Upgrade Guide

## License

This project is open-source. See the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting pull requests.

## Support

For support and questions, please open an issue on GitHub or contact the development team.

---

**Built with ❤️ for libraries worldwide**
