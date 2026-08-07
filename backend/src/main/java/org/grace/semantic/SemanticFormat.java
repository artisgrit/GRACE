package org.grace.semantic;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

enum SemanticFormat {
    JSON_LD("application/ld+json", RDFFormat.JSONLD11_PRETTY),
    TURTLE("text/turtle", RDFFormat.TURTLE_PRETTY),
    RDF_XML("application/rdf+xml", RDFFormat.RDFXML_PRETTY);

    private final String mediaType;
    private final RDFFormat rdfFormat;

    SemanticFormat(String mediaType, RDFFormat rdfFormat) {
        this.mediaType = mediaType;
        this.rdfFormat = rdfFormat;
    }

    String mediaType() {
        return mediaType;
    }

    String write(Model model) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RDFDataMgr.write(output, model, rdfFormat);
        return output.toString(StandardCharsets.UTF_8);
    }

    static SemanticFormat negotiate(String format, String accept) {
        String requested = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (requested.equals("ttl") || requested.equals("turtle")) return TURTLE;
        if (requested.equals("rdf") || requested.equals("xml") || requested.equals("rdfxml")) return RDF_XML;
        if (requested.equals("jsonld") || requested.equals("json-ld")) return JSON_LD;
        String accepted = accept == null ? "" : accept.toLowerCase(Locale.ROOT);
        if (accepted.contains("text/turtle")) return TURTLE;
        if (accepted.contains("application/rdf+xml")) return RDF_XML;
        return JSON_LD;
    }
}
