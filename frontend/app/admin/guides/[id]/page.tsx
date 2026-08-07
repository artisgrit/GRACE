import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import GuideEditor from "./GuideEditor";

export default async function GuideEditorPage({ params }: { params: Promise<{ id: string }> }) {
  if (!(await cookies()).has("grace_session")) redirect("/login");
  const { id } = await params;
  return <GuideEditor guideId={id} />;
}
