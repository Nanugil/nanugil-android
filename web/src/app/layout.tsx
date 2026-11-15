import type { Metadata, Viewport } from "next";
import "pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css";
import "./globals.css";
import EmotionRegistry from "@/ui/EmotionRegistry";

export const metadata: Metadata = {
  // 배포 주소를 NEXT_PUBLIC_SITE_URL로 주면 공유 미리보기 주소가 맞게 나온다
  metadataBase: process.env.NEXT_PUBLIC_SITE_URL ? new URL(process.env.NEXT_PUBLIC_SITE_URL) : undefined,
  title: "나누길",
  applicationName: "나누길",
  icons: { icon: [{ url: "/icon.svg", type: "image/svg+xml" }, { url: "/icon-192.png", sizes: "192x192" }], apple: "/apple-touch-icon.png" },
  appleWebApp: { capable: true, title: "나누길", statusBarStyle: "default" },
  // 로그인해야 쓰는 서비스라 검색 결과에 화면을 노출하지 않는다
  robots: { index: false, follow: false },
  openGraph: { title: "나누길", description: "함께 이동한 일행과 주유비와 통행료를 나누고, 정산 요청을 받아 바로 송금합니다.", images: ["/icon-512.png"], locale: "ko_KR", type: "website" },
  description: "함께 이동한 일행과 주유비와 통행료를 나누고, 정산 요청을 받아 바로 송금합니다.",
  manifest: "/manifest.webmanifest",
};

export const viewport: Viewport = { themeColor: "#FFFFFF", width: "device-width", initialScale: 1, viewportFit: "cover" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>
        <EmotionRegistry>{children}</EmotionRegistry>
      </body>
    </html>
  );
}
