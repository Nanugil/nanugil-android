import { createServerClient } from "@supabase/ssr";
import { NextResponse, type NextRequest } from "next/server";

const PUBLIC = ["/login", "/signup"];

/** 세션 쿠키를 갱신하고, 로그인 안 된 요청은 원래 주소를 next에 담아 로그인으로 보낸다. */
export async function middleware(req: NextRequest) {
  let res = NextResponse.next({ request: req });
  const sb = createServerClient(process.env.NEXT_PUBLIC_SUPABASE_URL!, process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!, {
    cookies: {
      getAll: () => req.cookies.getAll(),
      setAll: (list) => {
        list.forEach(({ name, value }) => req.cookies.set(name, value));
        res = NextResponse.next({ request: req });
        list.forEach(({ name, value, options }) => res.cookies.set(name, value, options));
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
    return NextResponse.redirect(url);
  }
  return res;
}

export const config = {
  // public 폴더의 파일(확장자가 있는 주소)은 로그인 검사에서 뺀다
  matcher: ["/((?!_next/static|_next/image|.*\\.[a-z0-9]+$).*)"],
};
