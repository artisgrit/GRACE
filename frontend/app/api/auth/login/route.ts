import { NextRequest, NextResponse } from "next/server";

const backendApi = process.env.BACKEND_API_URL ?? "http://localhost:8080/api/v1";

export async function POST(request: NextRequest) {
  const body = await request.text();
  const response = await fetch(`${backendApi}/auth/login`, {
    method: "POST",
    headers: { "content-type": "application/json", accept: "application/json" },
    body,
    cache: "no-store"
  });
  const payload = await response.text();
  const nextResponse = new NextResponse(payload, {
    status: response.status,
    headers: { "content-type": response.headers.get("content-type") ?? "application/json" }
  });

  if (response.ok) {
    const login = JSON.parse(payload) as { accessToken: string; expiresAt: string };
    nextResponse.cookies.set("grace_session", login.accessToken, {
      httpOnly: true,
      secure: process.env.COOKIE_SECURE === "true",
      sameSite: "lax",
      path: "/",
      expires: new Date(login.expiresAt)
    });
  }
  return nextResponse;
}
