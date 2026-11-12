"use client";
import styled from "@emotion/styled";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import { Button } from "@/ui/Button";
import { GlassCard } from "@/ui/GlassCard";
import { Icon } from "@/ui/Icon";
import { Screen } from "@/ui/Screen";
import { t } from "@/ui/theme";

const Title = styled.h1`font-size: ${t.text.x2};`;
const Info = styled.dl`margin: 0; display: grid;`;
const Row = styled.div`display: flex; align-items: center; justify-content: space-between; gap: ${t.space.sm};
  min-height: 56px; border-bottom: 1px solid ${t.color.rule}; &:last-child { border-bottom: 0; }`;
const Dt = styled.dt`color: ${t.color.ink2}; font-size: ${t.text.sm};`;
const Dd = styled.dd`margin: 0; font-weight: 600; min-width: 0; overflow-wrap: anywhere; text-align: right;`;
const Note = styled.p`font-size: ${t.text.xs}; color: ${t.color.muted};`;

/** 내 정보: 이름·이메일 확인과 로그아웃. 이메일은 나만 보인다. */
export function MeView({ name, email }: { name: string; email: string }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  return (
    <Screen>
      <Title>내 정보</Title>
      <GlassCard>
        <Info>
          <Row><Dt>이름</Dt><Dd>{name || "이름 없음"}</Dd></Row>
          <Row><Dt>이메일</Dt><Dd>{email}</Dd></Row>
        </Info>
        <Note>일행에게는 이름만 보이고 이메일은 보이지 않아요.</Note>
      </GlassCard>
      <Button type="button" variant="ghost" disabled={busy} aria-busy={busy} onClick={async () => {
        setBusy(true);
        await createBrowserSupabase().auth.signOut();
        router.replace("/login");
        router.refresh();
      }}><Icon name="logout" />로그아웃</Button>
    </Screen>
  );
}
