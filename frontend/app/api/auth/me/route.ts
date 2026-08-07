import { cookies } from "next/headers";
import { NextResponse } from "next/server";

const backendApi = process.env.BACKEND_API_URL ?? "http://localhost:8080/api/v1";

export async function GET() {
  const token = (await cookies()).get("grace_session")?.value;
  if (!token) return NextResponse.json({ code: "unauthenticated" }, { status: 401 });
  const response = await fetch(`${backendApi}/auth/me`, {
    headers: { authorization: `Bearer ${token}`, accept: "application/json" },
    cache: "no-store"
  });
  return new NextResponse(await response.text(), {
    status: response.status,
    headers: { "content-type": response.headers.get("content-type") ?? "application/json" }
  });
}
