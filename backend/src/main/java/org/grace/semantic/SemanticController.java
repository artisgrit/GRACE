package org.grace.semantic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.apache.jena.rdf.model.Model;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/tenants/{tenant}/semantic")
public class SemanticController {
    private final SemanticService semantic;

    public SemanticController(SemanticService semantic) { this.semantic = semantic; }

    @GetMapping("/graph")
    public ResponseEntity<String> graph(@PathVariable String tenant,
                                        @RequestParam(required=false) String format,
                                        @RequestHeader(value="Accept", required=false) String accept) {
        return response(semantic.graph(tenant), SemanticFormat.negotiate(format, accept));
    }

    @GetMapping("/subjects")
    public ResponseEntity<String> subjects(@PathVariable String tenant,
                                           @RequestParam(required=false) String format,
                                           @RequestHeader(value="Accept", required=false) String accept) {
        return response(semantic.subjects(tenant), SemanticFormat.negotiate(format, accept));
    }

    @GetMapping("/entities/{kind}/{id}")
    public ResponseEntity<String> entity(@PathVariable String tenant, @PathVariable String kind,
                                         @PathVariable UUID id, @RequestParam(required=false) String format,
                                         @RequestHeader(value="Accept", required=false) String accept) {
        return response(semantic.entity(tenant, kind, id), SemanticFormat.negotiate(format, accept));
    }

    private static ResponseEntity<String> response(Model model, SemanticFormat format) {
        String body = format.write(model);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(format.mediaType()))
            .cacheControl(CacheControl.noCache()).eTag('"' + hash(body) + '"')
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT).body(body);
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
