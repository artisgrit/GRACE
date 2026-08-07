package org.grace.guides;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/tenants/{tenantSlug}/guides")
public class PublicGuideController {
    private final GuideService guideService;

    public PublicGuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    @GetMapping
    public List<GuideSummary> list(@PathVariable String tenantSlug) {
        return guideService.listPublished(tenantSlug);
    }

    @GetMapping("/{slug}")
    public GuideDetail get(@PathVariable String tenantSlug, @PathVariable String slug) {
        return guideService.getPublished(tenantSlug, slug);
    }
}
