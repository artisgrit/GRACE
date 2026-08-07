import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

const publicRoutes = ["/", "/search", "/guides", "/resources", "/assistant", "/login"];

for (const route of publicRoutes) {
  test(`${route} has no automated WCAG A/AA violations`, async ({ page }, testInfo) => {
    await page.goto(route, { waitUntil: "networkidle" });
    const results = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
      .analyze();

    await testInfo.attach("axe-results", {
      body: JSON.stringify(results, null, 2),
      contentType: "application/json"
    });
    expect(results.violations).toEqual([]);
  });
}
