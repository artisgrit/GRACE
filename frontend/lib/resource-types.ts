export type ReadingListItem = { bookId: string; category: string; sectionTitle?: string | null; weekNumber?: number | null; notes?: string | null };
export type LibraryResource = {
  id: string; resourceType: string; title: string; slug: string; alternativeTitle?: string | null;
  description: string; status: string; provider?: string | null; publisher?: string | null;
  canonicalUrl?: string | null; proxyMode: string; accessType?: string | null; isbn?: string | null;
  authors: string[]; email?: string | null; phone?: string | null; orcid?: string | null;
  faqAnswer?: string | null; featured: boolean; openAccess: boolean; trialEndsAt?: string | null;
  helpfulVotes: number; unhelpfulVotes: number; metadata: Record<string, unknown>;
  subjectIds: string[]; guideIds: string[]; items: ReadingListItem[]; version: number; updatedAt: string;
};
export type Subject = {
  id: string; label: string; slug: string; alternativeLabels: string[]; description: string;
  notation?: string | null; broaderSubjectId?: string | null; broaderLabel?: string | null;
  relatedSubjectIds: string[]; guideIds: string[]; active: boolean;
};
