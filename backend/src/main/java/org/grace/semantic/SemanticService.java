package org.grace.semantic;

import java.net.URI;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class SemanticService {
    static final String SCHEMA = "https://schema.org/";
    static final String GRACE = "https://grace.library/ns#";
    private static final Resource SCHEMA_DATASET = resource(SCHEMA + "Dataset");
    private static final Resource SCHEMA_CREATIVE_WORK = resource(SCHEMA + "CreativeWork");
    private static final Resource SCHEMA_BOOK = resource(SCHEMA + "Book");
    private static final Resource SCHEMA_FAQ = resource(SCHEMA + "FAQPage");
    private static final Resource SCHEMA_PERSON = resource(SCHEMA + "Person");
    private static final Resource SCHEMA_ITEM_LIST = resource(SCHEMA + "ItemList");
    private static final Property NAME = property(SCHEMA + "name");
    private static final Property DESCRIPTION = property(SCHEMA + "description");
    private static final Property ABOUT = property(SCHEMA + "about");
    private static final Property HAS_PART = property(SCHEMA + "hasPart");
    private static final Property ITEM = property(SCHEMA + "itemListElement");
    private static final Property URL = property(SCHEMA + "url");
    private static final Property AUTHOR = property(SCHEMA + "author");
    private static final Property ISBN = property(SCHEMA + "isbn");
    private static final Property EMAIL = property(SCHEMA + "email");
    private static final Property DATE_MODIFIED = property(SCHEMA + "dateModified");
    private static final Property LIAISON = property(GRACE + "liaison");
    private static final Property RELATIONSHIP_TYPE = property(GRACE + "relationshipType");

    private final JdbcClient jdbc;
    private final String publicBaseUrl;

    public SemanticService(JdbcClient jdbc,
                           @Value("${grace.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.jdbc = jdbc;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public Model graph(String tenant) {
        requireTenant(tenant);
        Model model = model();
        String root = tenantRoot(tenant);
        Resource dataset = model.createResource(root + "/semantic/graph")
            .addProperty(RDF.type, SCHEMA_DATASET)
            .addProperty(NAME, "GRACE linked data for " + tenant)
            .addProperty(DCTerms.identifier, "urn:grace:tenant:" + tenant);
        Resource scheme = model.createResource(root + "/semantic/subjects")
            .addProperty(RDF.type, SKOS.ConceptScheme)
            .addProperty(SKOS.prefLabel, "GRACE subjects for " + tenant);

        Map<UUID, Resource> subjects = subjects(model, tenant, scheme, dataset);
        guides(model, tenant, subjects, dataset);
        resources(model, tenant, subjects, dataset);
        relationships(model, tenant);
        return model;
    }

    public Model subjects(String tenant) {
        Model source = graph(tenant);
        Model result = model();
        source.listSubjectsWithProperty(RDF.type, SKOS.ConceptScheme).forEachRemaining(subject -> copyOutgoing(source, result, subject));
        source.listSubjectsWithProperty(RDF.type, SKOS.Concept).forEachRemaining(subject -> copyOutgoing(source, result, subject));
        return result;
    }

    public Model entity(String tenant, String kind, UUID id) {
        String normalized = normalizeKind(kind);
        Model source = graph(tenant);
        Resource target = source.createResource(canonical(tenant, normalized, id));
        if (!source.containsResource(target)) throw new IllegalArgumentException("Published semantic entity was not found");
        Model result = model();
        copyOutgoing(source, result, target);
        source.listStatements(null, null, target).forEachRemaining(statement -> result.add(statement));
        result.listObjects().filterKeep(node -> node.isResource()).mapWith(node -> node.asResource()).toList()
            .forEach(related -> {
                source.listStatements(related, RDF.type, (org.apache.jena.rdf.model.RDFNode) null).forEachRemaining(result::add);
                source.listStatements(related, NAME, (org.apache.jena.rdf.model.RDFNode) null).forEachRemaining(result::add);
                source.listStatements(related, SKOS.prefLabel, (org.apache.jena.rdf.model.RDFNode) null).forEachRemaining(result::add);
            });
        return result;
    }

    public String canonical(String tenant, String kind, UUID id) {
        return tenantRoot(tenant) + "/semantic/entities/" + normalizeKind(kind) + "/" + id;
    }

    private Map<UUID, Resource> subjects(Model model, String tenant, Resource scheme, Resource dataset) {
        Map<UUID, Resource> result = new HashMap<>();
        record Subject(UUID id, String label, String definition, String notation, String[] alternatives, UUID broader) {}
        List<Subject> rows = jdbc.sql("""
            SELECT s.id, s.pref_label, s.definition, s.notation, s.alt_labels, s.broader_term_id
            FROM subjects s JOIN tenants t ON t.id=s.tenant_id
            WHERE t.slug=:tenant AND s.is_active=true ORDER BY s.pref_label
            """).param("tenant", tenant).query((rs, row) -> new Subject(
                rs.getObject("id", UUID.class), rs.getString("pref_label"), rs.getString("definition"),
                rs.getString("notation"), strings(rs.getArray("alt_labels")), rs.getObject("broader_term_id", UUID.class)
            )).list();
        rows.forEach(row -> {
            Resource concept = model.createResource(canonical(tenant, "subject", row.id()))
                .addProperty(RDF.type, SKOS.Concept).addProperty(SKOS.inScheme, scheme)
                .addProperty(SKOS.prefLabel, row.label()).addProperty(DCTerms.identifier, "urn:uuid:" + row.id());
            literal(concept, SKOS.definition, row.definition());
            literal(concept, SKOS.notation, row.notation());
            for (String alternative : row.alternatives()) literal(concept, SKOS.altLabel, alternative);
            dataset.addProperty(HAS_PART, concept);
            result.put(row.id(), concept);
        });
        rows.forEach(row -> { if (row.broader() != null && result.containsKey(row.broader())) result.get(row.id()).addProperty(SKOS.broader, result.get(row.broader())); });
        jdbc.sql("""
            SELECT rt.subject_id, rt.related_term_id FROM subject_related_terms rt
            JOIN subjects s ON s.id=rt.subject_id JOIN tenants t ON t.id=s.tenant_id
            WHERE t.slug=:tenant AND s.is_active=true
            """).param("tenant", tenant).query((rs, row) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).list()
            .forEach(link -> { if (result.containsKey(link.getKey()) && result.containsKey(link.getValue())) result.get(link.getKey()).addProperty(SKOS.related, result.get(link.getValue())); });
        return result;
    }

    private void guides(Model model, String tenant, Map<UUID, Resource> subjects, Resource dataset) {
        jdbc.sql("""
            SELECT g.id,g.title,g.description,g.tags,g.updated_at,gt.type_key FROM guides g
            JOIN tenants t ON t.id=g.tenant_id JOIN guide_types gt ON gt.id=g.guide_type_id
            WHERE t.slug=:tenant AND g.status='PUBLISHED' AND g.deleted_at IS NULL
            """).param("tenant", tenant).query((rs, row) -> {
                UUID id = rs.getObject("id", UUID.class);
                Resource guide = model.createResource(canonical(tenant, "guide", id))
                    .addProperty(RDF.type, SCHEMA_CREATIVE_WORK).addProperty(NAME, rs.getString("title"))
                    .addProperty(DCTerms.identifier, "urn:uuid:" + id).addProperty(property(GRACE + "guideType"), rs.getString("type_key"));
                literal(guide, DESCRIPTION, rs.getString("description"));
                instant(guide, rs.getTimestamp("updated_at"));
                for (String tag : strings(rs.getArray("tags"))) guide.addProperty(property(SCHEMA + "keywords"), tag);
                dataset.addProperty(HAS_PART, guide);
                return id;
            }).list();
        jdbc.sql("""
            SELECT gs.guide_id,gs.subject_id FROM guide_subjects gs JOIN guides g ON g.id=gs.guide_id
            JOIN tenants t ON t.id=g.tenant_id WHERE t.slug=:tenant AND g.status='PUBLISHED' AND g.deleted_at IS NULL
            """).param("tenant", tenant).query((rs, row) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).list()
            .forEach(link -> { Resource subject = subjects.get(link.getValue()); if (subject != null) model.createResource(canonical(tenant, "guide", link.getKey())).addProperty(ABOUT, subject); });
    }

    private void resources(Model model, String tenant, Map<UUID, Resource> subjects, Resource dataset) {
        jdbc.sql("""
            SELECT r.id,r.resource_type,r.title,r.description,r.canonical_url,r.isbn,r.authors,r.email,r.orcid,r.updated_at
            FROM library_resources r JOIN tenants t ON t.id=r.tenant_id
            WHERE t.slug=:tenant AND r.status='PUBLISHED' AND r.deleted_at IS NULL
            """).param("tenant", tenant).query((rs, row) -> {
                UUID id = rs.getObject("id", UUID.class);
                String type = rs.getString("resource_type");
                Resource item = model.createResource(canonical(tenant, "resource", id))
                    .addProperty(RDF.type, resourceType(type)).addProperty(NAME, rs.getString("title"))
                    .addProperty(DCTerms.identifier, "urn:uuid:" + id).addProperty(property(GRACE + "resourceType"), type);
                literal(item, DESCRIPTION, rs.getString("description"));
                literal(item, ISBN, rs.getString("isbn"));
                literal(item, EMAIL, rs.getString("email"));
                if (validHttpUri(rs.getString("canonical_url"))) item.addProperty(URL, model.createResource(rs.getString("canonical_url")));
                if (rs.getString("orcid") != null) item.addProperty(property(SCHEMA + "sameAs"), model.createResource("https://orcid.org/" + rs.getString("orcid").replace("https://orcid.org/", "")));
                for (String author : strings(rs.getArray("authors"))) item.addProperty(AUTHOR, author);
                instant(item, rs.getTimestamp("updated_at"));
                dataset.addProperty(HAS_PART, item);
                return id;
            }).list();
        jdbc.sql("""
            SELECT rs.resource_id,rs.subject_id FROM resource_subjects rs JOIN library_resources r ON r.id=rs.resource_id
            JOIN tenants t ON t.id=r.tenant_id WHERE t.slug=:tenant AND r.status='PUBLISHED' AND r.deleted_at IS NULL
            """).param("tenant", tenant).query((rs, row) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).list()
            .forEach(link -> { Resource subject = subjects.get(link.getValue()); if (subject != null) model.createResource(canonical(tenant, "resource", link.getKey())).addProperty(ABOUT, subject); });
    }

    private void relationships(Model model, String tenant) {
        jdbc.sql("""
            SELECT gr.guide_id,gr.resource_id,gr.relationship FROM guide_resources gr
            JOIN guides g ON g.id=gr.guide_id JOIN library_resources r ON r.id=gr.resource_id JOIN tenants t ON t.id=g.tenant_id
            WHERE t.slug=:tenant AND g.status='PUBLISHED' AND r.status='PUBLISHED' AND g.deleted_at IS NULL AND r.deleted_at IS NULL
            """).param("tenant", tenant).query((rs, row) -> new Object[]{rs.getObject(1, UUID.class),rs.getObject(2, UUID.class),rs.getString(3)}).list()
            .forEach(link -> model.createResource(canonical(tenant, "guide", (UUID) link[0]))
                .addProperty(HAS_PART, model.createResource(canonical(tenant, "resource", (UUID) link[1])))
                .addProperty(RELATIONSHIP_TYPE, (String) link[2]));
        jdbc.sql("""
            SELECT reading_list_id,book_id FROM reading_list_items rli JOIN library_resources rl ON rl.id=rli.reading_list_id
            JOIN library_resources b ON b.id=rli.book_id JOIN tenants t ON t.id=rl.tenant_id
            WHERE t.slug=:tenant AND rl.status='PUBLISHED' AND b.status='PUBLISHED' AND rl.deleted_at IS NULL AND b.deleted_at IS NULL
            """).param("tenant", tenant).query((rs, row) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).list()
            .forEach(link -> model.createResource(canonical(tenant, "resource", link.getKey())).addProperty(ITEM, model.createResource(canonical(tenant, "resource", link.getValue()))));
        jdbc.sql("""
            SELECT lsl.subject_id,lsl.librarian_id FROM librarian_subject_liaisons lsl
            JOIN library_resources l ON l.id=lsl.librarian_id JOIN tenants t ON t.id=l.tenant_id
            WHERE t.slug=:tenant AND l.status='PUBLISHED' AND l.deleted_at IS NULL
            """).param("tenant", tenant).query((rs, row) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).list()
            .forEach(link -> model.createResource(canonical(tenant, "subject", link.getKey())).addProperty(LIAISON, model.createResource(canonical(tenant, "resource", link.getValue()))));
    }

    private void requireTenant(String tenant) {
        boolean exists = jdbc.sql("SELECT count(*) > 0 FROM tenants WHERE slug=:tenant AND enabled=true")
            .param("tenant", tenant).query(Boolean.class).single();
        if (!exists) throw new IllegalArgumentException("Unknown tenant: " + tenant);
    }

    private String tenantRoot(String tenant) {
        return publicBaseUrl + "/api/v1/public/tenants/" + tenant;
    }

    private static Model model() {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("schema", SCHEMA); model.setNsPrefix("skos", SKOS.uri); model.setNsPrefix("dcterms", DCTerms.NS);
        model.setNsPrefix("grace", GRACE); model.setNsPrefix("rdf", RDF.uri); model.setNsPrefix("rdfs", RDFS.uri);
        return model;
    }

    private static void copyOutgoing(Model source, Model target, Resource subject) {
        source.listStatements(subject, null, (org.apache.jena.rdf.model.RDFNode) null).forEachRemaining(target::add);
    }

    private static String normalizeKind(String kind) {
        String value = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        if (!value.equals("subject") && !value.equals("guide") && !value.equals("resource")) throw new IllegalArgumentException("Semantic entity type must be subject, guide, or resource");
        return value;
    }

    private static Resource resource(String uri) { return ResourceFactory.createResource(uri); }
    private static Property property(String uri) { return ResourceFactory.createProperty(uri); }
    private static void literal(Resource resource, Property property, String value) { if (value != null && !value.isBlank()) resource.addProperty(property, value); }
    private static void instant(Resource resource, Timestamp timestamp) { if (timestamp != null) resource.addLiteral(DATE_MODIFIED, ResourceFactory.createTypedLiteral(timestamp.toInstant().toString(), XSDDatatype.XSDdateTime)); }
    private static boolean validHttpUri(String value) { try { URI uri=URI.create(value); return uri.getHost()!=null && (uri.getScheme().equals("http") || uri.getScheme().equals("https")); } catch(Exception ex) { return false; } }
    private static String[] strings(Array value) { try { return value == null ? new String[0] : (String[]) value.getArray(); } catch(Exception ex) { throw new IllegalStateException(ex); } }
    private static Resource resourceType(String type) { return switch(type) { case "BOOK" -> SCHEMA_BOOK; case "FAQ" -> SCHEMA_FAQ; case "LIBRARIAN" -> SCHEMA_PERSON; case "READING_LIST" -> SCHEMA_ITEM_LIST; case "DATABASE" -> SCHEMA_DATASET; default -> SCHEMA_CREATIVE_WORK; }; }
}
