import type { NextConfig } from "next";

// 기본 보안 헤더 (HSTS는 Vercel이 붙인다). 이 앱은 다른 사이트 안에 iframe으로 들어갈 일이 없다.
const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
  // 스크립트 출처까지 막는 전체 CSP는 Next 인라인 스크립트 nonce 설정이 필요해 다음 단계로 둔다
  { key: "Content-Security-Policy", value: "frame-ancestors 'none'; base-uri 'self'; object-src 'none'; form-action 'self'" },
];

const nextConfig: NextConfig = {
  compiler: { emotion: true },
  poweredByHeader: false,
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
