"use client";
import styled from "@emotion/styled";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import { Avatar, Item, ItemText, List, Section, Title } from "@/ui/kit";
import { Screen } from "@/ui/Screen";
import { t } from "@/ui/theme";

const Top = styled.header`padding-top: 16px;`;
const Logout = styled.button`display: flex; align-items: center; width: 100%; min-height: 56px; padding: 0; border: 0; background: none;
  font-size: 15px; color: ${t.color.muted}; cursor: pointer;
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; border-radius: 8px; }
  &:disabled { cursor: progress; }`;

/** 내 정보: 이름·이메일과 로그아웃 */
export function MeView({ name, email }: { name: string; email: string }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  return (
    <Screen>
      <Top><Title>내 정보</Title></Top>
      <Section>
        <List>
          <Item><Avatar aria-hidden="true">{(name || "?").slice(0, 1)}</Avatar><ItemText><strong>{name || "이름 없음"}</strong><span>{email}</span></ItemText></Item>
          <Item><ItemText><strong>이메일 공개 범위</strong><span>로그인에만 사용하며 그룹 멤버에게 공개되지 않습니다.</span></ItemText></Item>
        </List>
      </Section>
      <Section>
        <Logout type="button" disabled={busy} aria-busy={busy} onClick={async () => {
          setBusy(true);
          try { await createBrowserSupabase().auth.signOut(); } finally { setBusy(false); }
          router.replace("/login");
          router.refresh();
        }}>{busy ? "로그아웃 중" : "로그아웃"}</Logout>
      </Section>
    </Screen>
  );
}
