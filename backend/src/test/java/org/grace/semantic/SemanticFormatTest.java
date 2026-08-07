package org.grace.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

class SemanticFormatTest {
    @Test
    void negotiatesSupportedRdfRepresentations() {
        assertEquals(SemanticFormat.JSON_LD, SemanticFormat.negotiate(null, "application/ld+json"));
        assertEquals(SemanticFormat.TURTLE, SemanticFormat.negotiate(null, "text/turtle"));
        assertEquals(SemanticFormat.RDF_XML, SemanticFormat.negotiate("rdfxml", "*/*"));
    }

    @Test
    void serializesAStandardsBasedGraph() {
        Model model = ModelFactory.createDefaultModel();
        model.createResource("https://example.edu/resource/1").addProperty(RDF.type, model.createResource("https://schema.org/Book"));

        assertTrue(SemanticFormat.JSON_LD.write(model).contains("schema.org/Book"));
        assertTrue(SemanticFormat.TURTLE.write(model).contains("schema.org/Book"));
        assertTrue(SemanticFormat.RDF_XML.write(model).contains("rdf:RDF"));
    }
}
