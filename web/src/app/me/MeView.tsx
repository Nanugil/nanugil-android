"use client";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import { Button } from "@/ui/Button";
import { Icon } from "@/ui/Icon";
import { Body, IconBox, Label, Left, Line, PageHeader, Row, Rows, Stack, Tile } from "@/ui/kit";
import { Screen } from "@/ui/Screen";

/** 내 정보: 이름·이메일 확인과 로그아웃. 이메일은 나만 보인다. */
export function MeView({ name, email }: { name: string; email: string }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  return (
    <Screen>
      <PageHeader title="내 정보" />
      <Tile pad="md">
        <Line><Left><IconBox tone="dark">{name ? name.slice(0, 1) : <Icon name="person" />}</IconBox>
          <Stack><b>{name || "이름 없음"}</b><Label>{email}</Label></Stack></Left></Line>
      </Tile>
      <Body>
        <Tile>
          <Rows>
            <Row><IconBox><Icon name="visibility_off" /></IconBox><Stack style={{ flex: 1 }}><b>이메일은 나만 봐요</b><Label>일행에게는 이름만 보여요</Label></Stack></Row>
            <Row><IconBox><Icon name="payments" /></IconBox><Stack style={{ flex: 1 }}><b>송금은 카카오페이로</b><Label>나누길은 돈을 직접 옮기지 않아요</Label></Stack></Row>
          </Rows>
        </Tile>
      </Body>
      <Button type="button" variant="ghost" disabled={busy} aria-busy={busy} onClick={async () => {
        setBusy(true);
        await createBrowserSupabase().auth.signOut();
        router.replace("/login");
        router.refresh();
      }}><Icon name="logout" />{busy ? "로그아웃하는 중" : "로그아웃"}</Button>
    </Screen>
  );
}
