import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import AdminConsole from "./AdminConsole";

export default async function AdminPage() {
  if (!(await cookies()).has("grace_session")) redirect("/login");
  return <AdminConsole />;
}
