import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import GuidesWorkspace from "./GuidesWorkspace";

export default async function GuidesPage() {
  if (!(await cookies()).has("grace_session")) redirect("/login");
  return <GuidesWorkspace />;
}
