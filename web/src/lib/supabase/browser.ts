import { createBrowserClient } from "@supabase/ssr";
import type { SupabaseClient } from "@supabase/supabase-js";

let client: SupabaseClient | undefined;

/** 브라우저에서 쓰는 Supabase 연결 (세션은 쿠키). 한 번만 만든다. */
export function createBrowserSupabase(): SupabaseClient {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL, key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  // 배포 환경변수가 빠지면 알 수 없는 네트워크 오류 대신 원인을 바로 알린다
  if (!url || !key) throw new Error("NEXT_PUBLIC_SUPABASE_URL / NEXT_PUBLIC_SUPABASE_ANON_KEY 환경변수가 없습니다.");
  client ??= createBrowserClient(url, key);
  return client;
}
