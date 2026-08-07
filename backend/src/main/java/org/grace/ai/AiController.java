package org.grace.ai;

import jakarta.validation.Valid;
import java.util.Map;
import org.grace.security.TenantAccess;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class AiController {
    private final AiService ai; private final TenantAccess tenants;
    public AiController(AiService ai,TenantAccess tenants){this.ai=ai;this.tenants=tenants;}
    @GetMapping("/tenants/{tenant}/ai/configuration") @PreAuthorize("hasAuthority('ai.configure')")
    public AiConfiguration configuration(@PathVariable String tenant,Authentication auth){tenants.require(tenant,auth);return ai.configuration(tenant);}
    @PutMapping("/tenants/{tenant}/ai/configuration") @PreAuthorize("hasAuthority('ai.configure')")
    public AiConfiguration save(@PathVariable String tenant,Authentication auth,@Valid @RequestBody AiConfigurationRequest request){tenants.require(tenant,auth);return ai.save(tenant,request);}
    @PostMapping("/tenants/{tenant}/ai/reindex") @PreAuthorize("hasAuthority('ai.configure')")
    public Map<String,Object> reindex(@PathVariable String tenant,Authentication auth){tenants.require(tenant,auth);return ai.reindexEmbeddings(tenant);}
    @GetMapping("/tenants/{tenant}/ai/usage") @PreAuthorize("hasAuthority('ai.configure')")
    public Map<String,Object> usage(@PathVariable String tenant,Authentication auth){tenants.require(tenant,auth);return ai.usageSummary(tenant);}
    @PostMapping("/public/tenants/{tenant}/ai/search")
    public HybridSearchResponse search(@PathVariable String tenant,@Valid @RequestBody AssistantRequest request,@RequestParam(defaultValue="10") int size){return ai.hybridSearch(tenant,request.question(),size);}
    @PostMapping("/public/tenants/{tenant}/ai/assistant")
    public AssistantResponse assistant(@PathVariable String tenant,@Valid @RequestBody AssistantRequest request){return ai.answer(tenant,request.question());}
}
