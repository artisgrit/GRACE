import type { Metadata } from "next";
import { notFound } from "next/navigation";
import GuideRenderer from "../../../components/guides/GuideRenderer";
import type { GuideDetail } from "../../../lib/guide-types";

const backendApi = process.env.BACKEND_API_URL ?? "http://localhost:8080/api/v1";
const tenant = process.env.GRACE_TENANT_SLUG ?? "default";

async function getGuide(slug: string) {
  const response = await fetch(`${backendApi}/public/tenants/${tenant}/guides/${encodeURIComponent(slug)}`, { cache: "no-store" });
  if (response.status === 400 || response.status === 404) return null;
  if (!response.ok) throw new Error("Guide service is unavailable");
  return response.json() as Promise<GuideDetail>;
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const guide = await getGuide((await params).slug);
  return guide ? { title: guide.seoTitle || guide.title, description: guide.seoDescription || guide.description } : { title: "Guide not found" };
}

export default async function PublicGuidePage({ params }: { params: Promise<{ slug: string }> }) {
  const guide = await getGuide((await params).slug);
  if (!guide) notFound();
  return <main className="public-guide-shell"><a className="back-link" href="/guides">Back to guides</a><GuideRenderer guide={guide} /></main>;
}
