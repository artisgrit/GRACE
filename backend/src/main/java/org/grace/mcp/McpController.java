package org.grace.mcp;

import jakarta.validation.Valid;
import java.util.Map;
import org.grace.security.TenantAccess;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class McpController {
    private final McpProtocolService protocol;
    private final TenantAccess tenants;

    public McpController(McpProtocolService protocol, TenantAccess tenants) { this.protocol=protocol; this.tenants=tenants; }

    @PostMapping(value="/api/v1/mcp/tenants/{tenant}", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('mcp.read')")
    public ResponseEntity<?> post(@PathVariable String tenant, Authentication authentication,
                                  @Valid @RequestBody McpRequest request,
                                  @RequestHeader(value="Mcp-Session-Id",required=false) String sessionId,
                                  @RequestHeader(value="MCP-Protocol-Version",required=false) String protocolVersion) {
        tenants.require(tenant,authentication);
        try {
            McpExchange exchange=protocol.handle(tenant,request,sessionId,protocolVersion,(Jwt)authentication.getPrincipal());
            ResponseEntity.BodyBuilder response=ResponseEntity.status(exchange.status()).header("MCP-Protocol-Version",McpProtocolService.PROTOCOL_VERSION);
            if(exchange.sessionId()!=null)response.header("Mcp-Session-Id",exchange.sessionId());
            return exchange.body()==null?response.build():response.body(exchange.body());
        } catch(McpProtocolService.McpSessionException ex) {
            return ResponseEntity.badRequest().body(Map.of("jsonrpc","2.0","id",request.id(),"error",Map.of("code",-32000,"message",ex.getMessage())));
        }
    }

    @DeleteMapping("/api/v1/mcp/tenants/{tenant}")
    @PreAuthorize("hasAuthority('mcp.read')")
    public ResponseEntity<Void> close(@PathVariable String tenant,Authentication authentication,
                                      @RequestHeader("Mcp-Session-Id") String sessionId) {
        tenants.require(tenant,authentication);
        return protocol.close(tenant,sessionId,(Jwt)authentication.getPrincipal())?ResponseEntity.noContent().build():ResponseEntity.notFound().build();
    }
}
