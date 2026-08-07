import { cookies } from "next/headers";import { redirect } from "next/navigation";import QualityDashboard from "./QualityDashboard";
export default async function QualityPage(){if(!(await cookies()).has("grace_session"))redirect("/login");return <QualityDashboard/>;}
