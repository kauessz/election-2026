import { NextResponse } from "next/server";

export function proxy() {
  if (process.env.ADMIN_UI_ENABLED !== "true") {
    return new NextResponse("Não encontrado", {
      status: 404,
      headers: { "Cache-Control": "private, no-store" },
    });
  }
  return NextResponse.next();
}

export const config = { matcher: "/admin/:path*" };
