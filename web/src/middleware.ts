import { createServerClient } from "@supabase/ssr";
import { NextResponse, type NextRequest } from "next/server";

const PUBLIC = ["/login", "/signup", "/auth/confirm"];

/** 세션 쿠키를 갱신하고, 로그인 안 된 요청은 원래 주소를 next에 담아 로그인으로 보낸다. */
export async function middleware(req: NextRequest) {
  let res = NextResponse.next({ request: req });
  const sb = createServerClient(process.env.NEXT_PUBLIC_SUPABASE_URL!, process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!, {
    cookies: {
      getAll: () => req.cookies.getAll(),
      setAll: (list, headers) => {
        list.forEach(({ name, value }) => req.cookies.set(name, value));
        res = NextResponse.next({ request: req });
        list.forEach(({ name, value, options }) => res.cookies.set(name, value, options));
        // 인증 응답이 캐시되지 않게 하는 헤더(@supabase/ssr가 넘겨줌)
        Object.entries(headers ?? {}).forEach(([k, v]) => res.headers.set(k, String(v)));
      },
    },
  });
  const { data: { user } } = await sb.auth.getUser();
  // 개발 전용 미리보기(/preview)는 로그인 없이 연다. 배포 빌드에서는 페이지가 404를 낸다.
  const preview = process.env.NODE_ENV !== "production" && req.nextUrl.pathname.startsWith("/preview");
  if (!user && !preview && !PUBLIC.includes(req.nextUrl.pathname)) {
    const url = req.nextUrl.clone();
    url.pathname = "/login";
    url.search = `?next=${encodeURIComponent(req.nextUrl.pathname + req.nextUrl.search)}`;
    const redirect = NextResponse.redirect(url);
    // 만료된 세션을 정리하며 바뀐 쿠키도 함께 보낸다 (안 보내면 낡은 쿠키가 남아 매번 갱신을 시도한다)
    res.cookies.getAll().forEach((c) => redirect.cookies.set(c));
    res.headers.forEach((v, k) => { if (k.toLowerCase() === "cache-control") redirect.headers.set(k, v); });
    return redirect;
  }
  return res;
}

export const config = {
  // public 폴더의 파일(확장자가 있는 주소)은 로그인 검사에서 뺀다
  matcher: ["/((?!_next/static|_next/image|.*\\.[a-z0-9]+$).*)"],
};
