import type { Metadata, Viewport } from "next";
import "pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css";
import "material-symbols/rounded.css";
import "./globals.css";
import EmotionRegistry from "@/ui/EmotionRegistry";

export const metadata: Metadata = {
  title: "나누길",
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
