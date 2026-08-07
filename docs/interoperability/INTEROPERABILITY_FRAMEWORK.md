# GRACE Interoperability Framework

## Overview

GRACE implements a comprehensive interoperability framework enabling seamless integration with academic, library, research, and learning systems worldwide.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    GRACE Core Platform                       │
├─────────────────────────────────────────────────────────────┤
│  Integration Gateway (API Gateway, Auth, Rate Limiting)     │
├─────────────────────────────────────────────────────────────┤
│  Standards Layer (OAI-PMH, SRU, Z39.50, LTI, OpenURL)       │
├─────────────────────────────────────────────────────────────┤
│  Metadata Mapping Layer (Crosswalks, Validation, PROV)      │
├─────────────────────────────────────────────────────────────┤
│  Connector Framework (Plugin-based, Isolated, Testable)     │
└─────────────────────────────────────────────────────────────┘
         │           │           │           │
    ┌────▼────┐ ┌────▼────┐ ┌────▼────┐ ┌────▼────┐
    │ Library │ │Repository│ │  LMS   │ │Identity │
    │Systems  │ │ Systems  │ │Systems │ │ Systems │
    └─────────┘ └─────────┘ └─────────┘ └─────────┘
```

## Supported System Categories

### Library Systems
- **ILS/LSP**: Koha, FOLIO, Alma, Voyager, Aleph, Sierra, Symphony, Evergreen
- **Discovery**: VuFind, Blacklight, Primo, Summon, EDS, WorldCat Discovery
- **Link Resolvers**: SFX, 360 Link, Alma Link Resolver, Full Text Finder

### Repository Systems
- **Institutional Repositories**: DSpace, EPrints, Fedora, Islandora, InvenioRDM
- **Research Data**: Dataverse, Zenodo, Figshare, CKAN
- **Publishing**: OJS, Janeway, OMP, Pressbooks

### Research Information
- **CRIS/RIMS**: DSpace-CRIS, Pure, Symplectic Elements, VIVO, Converis
- **Identifier Services**: ORCID, ROR, VIAF, ISNI, DOI resolvers
- **Metadata Aggregators**: Crossref, DataCite, OpenAlex, Unpaywall, DOAJ

### Learning Systems
- **LMS**: Moodle, Canvas, Blackboard, Brightspace, Sakai, Open edX
- **SIS**: Banner, PeopleSoft, Ellucian, Unit4
- **Standards**: LTI 1.3, OneRoster, Ed-Fi

### Archives & Collections
- **Archives**: ArchivesSpace, AtoM, Archivists' Toolkit
- **Digital Collections**: Omeka, CollectiveAccess, CollectionSpace
- **IIIF**: Mirador, Universal Viewer compatible

## Protocols & Standards

### Search & Retrieval
- REST APIs, GraphQL, OData
- OAI-PMH, ResourceSync
- SRU/SRW, CQL
- Z39.50, SIP2, NCIP

### Authentication & Authorization
- OAuth 2.0, OpenID Connect
- SAML 2.0, Shibboleth
- LDAP, Active Directory
- SCIM 2.0

### Metadata
- Dublin Core, Qualified DC
- MARC21, MARCXML, BIBFRAME
- MODS, METS, EAD, EAC-CPF
- Schema.org, JSON-LD, RDF
- SKOS, FOAF, PROV-O
- DataCite, Crossref schemas
- KBART, COUNTER, SUSHI

### Learning & Identity
- LTI 1.3 Advantage
- OneRoster, Ed-Fi
- xAPI, SCORM

### Digital Objects
- IIIF Image API, Presentation API
- SWORD, SWORD v2
- BagIt, OCFL

## Connector Framework

### Interface Definition

All connectors implement `LibrarySystemConnector<T, R, S>`:

```java
interface LibrarySystemConnector<T, R, S> {
    String getConnectorType();
    String getName();
    String getVersion();
    Map<String, Object> getConfigSchema();
    void initialize(T config);
    boolean testConnection();
    ConnectorHealth healthCheck();
    boolean authenticate();
    ConnectorResult<S> search(String query, int maxResults);
    Optional<ConnectorRecord<R>> getRecord(String id);
    Optional<AvailabilityInfo> getAvailability(String itemId);
    Optional<HoldingsInfo> getHoldings(String bibId);
    ConnectorResult<S> getCollection(String collectionId, int offset, int limit);
    HarvestResult<R> harvest(Instant from, Instant until);
    SyncResult sync(SyncOptions syncOptions);
    void close();
}
```

### Capabilities

- Connection testing and health monitoring
- Authentication with multiple methods
- Search with pagination and facets
- Record retrieval by identifier
- Holdings and availability checking
- Batch harvesting with resumption tokens
- Incremental and full synchronization
- Graceful degradation on failures

## Metadata Mapping

### Crosswalk Engine

Configurable metadata transformations:

```
External Field          →  GRACE Field
─────────────────────────────────────────
dc.title               →  title
dc.creator             →  creators[]
dc.subject             →  subjects[]
dc.identifier.isbn     →  isbn
dc.identifier.issn     →  issn
dc.identifier.doi      →  doi
dc.identifier.uri      →  sourceUrl
dc.date.issued         →  publicationDate
dc.rights              →  license
```

### Supported Formats

**Input**: MARC21, MARCXML, Dublin Core, MODS, METS, EAD, DataCite XML, Crossref XML, KBART, RIS, BibTeX, CSL-JSON

**Output**: JSON-LD, Schema.org, Dublin Core, MARCXML, BIBFRAME, RIS, BibTeX, CSL-JSON, RDF/XML

## Integration Patterns

### 1. Real-time API Integration
For systems with modern REST/GraphQL APIs (DSpace 7+, Koha 20+, FOLIO)

### 2. Protocol-based Harvesting
For standards-compliant systems (OAI-PMH, SRU, Z39.50)

### 3. File-based Import
For legacy systems or bulk operations (CSV, XML, MARC via SFTP)

### 4. Event-driven Sync
For systems supporting webhooks or message queues

### 5. Federated Search
Query multiple sources simultaneously with result merging

## Security Considerations

- Credentials encrypted at rest
- API keys scoped and revocable
- Rate limiting per connector
- Circuit breakers prevent cascade failures
- SSRF protection for external fetches
- CSP headers for embedded content
- Audit logging for all integrations

## Resilience Features

- Timeout configuration per connector
- Bounded retry with exponential backoff
- Circuit breaker pattern
- Graceful degradation
- Cached fallback data
- Queue-based background jobs

## Administration

### Integration Dashboard

Administrators can:
1. Add/configure integrations
2. Test connections
3. Map metadata fields
4. Schedule synchronization
5. Monitor health status
6. View error logs
7. Manage API keys

### Health Monitoring

Each connector tracks:
- Enabled/disabled status
- Connection health (healthy/degraded/unhealthy)
- Last successful connection
- Last error time and message
- Records processed count
- Next scheduled sync

## Extensibility

### Plugin SDK

Custom connectors can be developed as plugins:

```json
{
  "name": "custom-connector",
  "version": "1.0.0",
  "apiVersion": "^2.0.0",
  "connectorType": "custom",
  "entryPoint": "./dist/index.js",
  "configSchema": {...},
  "capabilities": ["search", "harvest", "sync"]
}
```

### Event Hooks

Connectors can listen for domain events:
- `guide.published`
- `course.updated`
- `database.changed`
- `repository.item.harvested`

## Future Roadmap

### Phase 2A: Foundation ✓
- Connector framework
- API gateway
- Secret management
- Scheduler service

### Phase 2B: Library Protocols
- OAI-PMH harvester/provider
- SRU/SRW client
- Z39.50 client
- OpenURL generator

### Phase 2C: Core Connectors
- DSpace REST + OAI-PMH
- Koha REST + SRU
- VuFind API
- OJS harvesting

### Phase 2D: Academic Systems
- LTI 1.3 complete
- SIS course import
- External identity sync

### Phase 2E: Scholarly Infrastructure
- ORCID integration
- ROR organization lookup
- Crossref/DataCite metadata
- Citation export (RIS, BibTeX)

### Phase 2F: Archives & Collections
- ArchivesSpace connector
- AtoM integration
- Omeka/Omeka S
- IIIF manifest embedding

### Phase 2G: Advanced Federation
- Federated search across sources
- GRACE-to-GRACE exchange
- SPARQL endpoint integration
- Knowledge graph exports

### Phase 2H: AI Interoperability
- MCP server tools
- RAG over connected systems
- Agent permission controls

## Testing Strategy

Every connector includes:
- Unit tests for transformation logic
- Mocked integration tests
- Connection test suite
- Malformed response handling
- Authentication failure scenarios
- Timeout and rate-limit tests
- Pagination verification

## Documentation Requirements

Each connector documents:
- Supported system versions
- Required permissions/API keys
- Configuration parameters
- Metadata mappings
- Known limitations
- Troubleshooting guide
- Example configurations
