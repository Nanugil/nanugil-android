import { createServerClient } from "@supabase/ssr";
import type { SupabaseClient } from "@supabase/supabase-js";
import { cookies } from "next/headers";

/** 서버 컴포넌트에서 쓰는 Supabase 연결. 요청 쿠키의 세션으로 RLS가 적용된다. */
export async function createServerSupabase(): Promise<SupabaseClient> {
  const store = await cookies();
  return createServerClient(process.env.NEXT_PUBLIC_SUPABASE_URL!, process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!, {
    cookies: {
      getAll: () => store.getAll(),
      setAll: (list) => {
        // 서버 컴포넌트에서는 쿠키를 쓸 수 없다. 세션 갱신은 middleware가 한다.
        try { list.forEach(({ name, value, options }) => store.set(name, value, options)); } catch {}
      },
    },
  });
}
