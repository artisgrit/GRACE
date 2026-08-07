import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import ResourceWorkspace from "./ResourceWorkspace";

export default async function ResourcesAdminPage() {
  if (!(await cookies()).has("grace_session")) redirect("/login");
  return <ResourceWorkspace />;
}
