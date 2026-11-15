import type { EmailOtpType } from "@supabase/supabase-js";
import { NextResponse, type NextRequest } from "next/server";
import { safeNext } from "@/lib/next";
import { createServerSupabase } from "@/lib/supabase/server";

/**
 * 가입 확인 메일 링크가 도착하는 곳. Confirm email을 켠 실서비스에서 쓴다.
 * - 이메일 템플릿 방식: ?token_hash=…&type=email(&next=/join?code=…)
 * - PKCE 방식: ?code=…(&next=…)
 * 확인되면 세션 쿠키를 저장하고 원래 가려던 곳(같은 사이트 경로만)으로 보낸다.
 */
export async function GET(req: NextRequest) {
  const q = req.nextUrl.searchParams;
  const next = safeNext(q.get("next"));
  const sb = await createServerSupabase();
  const tokenHash = q.get("token_hash");
  const type = q.get("type") as EmailOtpType | null;
  const code = q.get("code");
  const { error } = tokenHash && type
    ? await sb.auth.verifyOtp({ type, token_hash: tokenHash })
    : code ? await sb.auth.exchangeCodeForSession(code) : { error: new Error("missing token") };
  const to = req.nextUrl.clone();
  to.search = "";
  if (error) {
    to.pathname = "/login";
    to.searchParams.set("next", next);
    to.searchParams.set("confirm", "failed");
  } else {
    const target = new URL(next, req.nextUrl.origin);
    to.pathname = target.pathname;
    to.search = target.search;
  }
  return NextResponse.redirect(to);
}
