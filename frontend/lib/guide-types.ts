export type GuideType = { id: string; key: string; displayName: string; description: string };
export type GuideTemplate = { id: string; key: string; name: string; description: string; guideTypeKey: string };
export type GuideSummary = {
  id: string;
  title: string;
  slug: string;
  description: string;
  guideTypeName: string;
  status: string;
  ownerName: string;
  revisionNumber: number;
  updatedAt: string;
};
export type GuideBlock = {
  id: string;
  blockType: string;
  position: number;
  content: Record<string, unknown>;
  settings: Record<string, unknown>;
};
export type GuidePage = {
  id: string;
  title: string;
  slug: string;
  position: number;
  layout: Record<string, unknown>;
  blocks: GuideBlock[];
};
export type GuideDetail = {
  id: string;
  tenantSlug: string;
  title: string;
  slug: string;
  description: string;
  guideTypeKey: string;
  guideTypeName: string;
  status: string;
  ownerName: string;
  tags: string[];
  metadata: Record<string, unknown>;
  featuredImageUrl?: string | null;
  seoTitle?: string | null;
  seoDescription?: string | null;
  revisionNumber: number;
  version: number;
  updatedAt: string;
  pages: GuidePage[];
};
export type GuideRevision = {
  id: string;
  revisionNumber: number;
  authorName: string;
  changeSummary: string;
  createdAt: string;
};
