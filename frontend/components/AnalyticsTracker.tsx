"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";

export default function AnalyticsTracker({ tenant }: { tenant: string }) {
  const pathname = usePathname();
  useEffect(() => {
    if (pathname.startsWith("/admin") || pathname === "/login") return;
    const eventType = pathname.startsWith("/guides/") ? "GUIDE_VIEW" : "PAGE_VIEW";
    void fetch(`/api/grace/public/tenants/${tenant}/analytics/events`, {
      method: "POST", headers: { "content-type": "application/json" }, keepalive: true,
      body: JSON.stringify({ eventType, path: pathname, metadata: {} })
    });
  }, [pathname, tenant]);

  useEffect(() => {
    function trackClick(event: MouseEvent) {
      const link = (event.target as Element | null)?.closest("a");
      if (!link || !link.closest(".directory-results")) return;
      void fetch(`/api/grace/public/tenants/${tenant}/analytics/events`, {
        method: "POST", headers: { "content-type": "application/json" }, keepalive: true,
        body: JSON.stringify({ eventType: "RESOURCE_CLICK", path: new URL(link.href).pathname, metadata: {} })
      });
    }
    document.addEventListener("click", trackClick);
    return () => document.removeEventListener("click", trackClick);
  }, [tenant]);
  return null;
}
