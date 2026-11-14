"use client";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import { Button } from "@/ui/Button";
import { Avatar, Item, ItemText, List, PageHeader, Section } from "@/ui/kit";
import { Screen } from "@/ui/Screen";

/** 내 정보: 이름·이메일 확인과 로그아웃. */
export function MeView({ name, email }: { name: string; email: string }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  return (
    <Screen>
      <PageHeader title="내 정보" />
      <Section>
        <List>
          <Item><Avatar aria-hidden="true">{(name || "?").slice(0, 1)}</Avatar><ItemText><strong>{name || "이름 없음"}</strong><span>{email}</span></ItemText></Item>
        </List>
      </Section>
      <Section>
        <List>
          <Item><ItemText><strong>이메일 공개 범위</strong><span>로그인에만 사용하며 그룹 멤버에게 공개되지 않습니다.</span></ItemText></Item>
          <Item><ItemText><strong>송금 방식</strong><span>송금은 카카오페이에서 직접 진행합니다.</span></ItemText></Item>
        </List>
      </Section>
      <Button type="button" variant="ghost" disabled={busy} aria-busy={busy} onClick={async () => {
        setBusy(true);
        await createBrowserSupabase().auth.signOut();
        router.replace("/login");
        router.refresh();
      }}>{busy ? "로그아웃 중" : "로그아웃"}</Button>
    </Screen>
  );
}
