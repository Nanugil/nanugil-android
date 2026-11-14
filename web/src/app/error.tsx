"use client";
import { useEffect } from "react";
import { Button, ButtonLink } from "@/ui/Button";
import { Hint, Lead, Section, Title } from "@/ui/kit";
import { Screen } from "@/ui/Screen";

/** 예상하지 못한 오류가 나도 빈 화면 대신 다시 시도할 길을 준다. */
export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => { console.error(error); }, [error]);
  return (
    <Screen nav={false}>
      <Section role="alert">
        <Title>문제가 발생했습니다</Title>
        <Lead>일시적인 오류일 수 있습니다.</Lead>
        <Hint>잠시 후 다시 시도해 주세요. 문제가 계속되면 앱을 새로고침해 주세요.{error.digest ? ` (오류 번호 ${error.digest})` : ""}</Hint>
        <div style={{ display: "grid", gap: 8 }}>
          <Button type="button" onClick={reset}>다시 시도</Button>
          <ButtonLink href="/" variant="ghost">홈으로</ButtonLink>
        </div>
      </Section>
    </Screen>
  );
}
