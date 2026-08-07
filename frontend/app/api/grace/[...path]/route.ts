import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

const backendApi = process.env.BACKEND_API_URL ?? "http://localhost:8080/api/v1";

async function proxy(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const token = (await cookies()).get("grace_session")?.value;
  if (!token) return NextResponse.json({ code: "unauthenticated" }, { status: 401 });
  const { path } = await context.params;
  const target = new URL(`${backendApi}/${path.map(encodeURIComponent).join("/")}`);
  request.nextUrl.searchParams.forEach((value, key) => target.searchParams.append(key, value));
  const response = await fetch(target, {
    method: request.method,
    headers: {
      authorization: `Bearer ${token}`,
      accept: "application/json",
      ...(request.headers.get("content-type") ? { "content-type": request.headers.get("content-type")! } : {})
    },
    body: request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer(),
    cache: "no-store"
  });
  return new NextResponse(await response.arrayBuffer(), {
    status: response.status,
    headers: { "content-type": response.headers.get("content-type") ?? "application/json" }
  });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
