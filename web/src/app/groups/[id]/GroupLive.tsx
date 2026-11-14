"use client";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { subscribeGroup } from "@/lib/api";
import { askNotificationPermission, notifySettlement } from "@/lib/notify";
import { applyTripChange } from "@/lib/tripChange";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import type { Group, Member, Trip } from "@/lib/types";
import { Button, ButtonLink } from "@/ui/Button";
import { Avatar, Hint, Item, ItemText, Lead, List, PageHeader, Section, SectionHead, Tag } from "@/ui/kit";
import { Screen } from "@/ui/Screen";

type State = { kind: "error" } | { kind: "ok"; group: Group; members: Member[]; trips: Trip[]; me: string };

export function GroupLive({ state }: { state: State }) {
  const router = useRouter();
  const [trips, setTrips] = useState(state.kind === "ok" ? state.trips : []);
  const tripsRef = useRef(trips);
  const [perm, setPerm] = useState<NotificationPermission | "unsupported">("granted");

  useEffect(() => { setPerm(typeof Notification === "undefined" ? "unsupported" : Notification.permission); }, []);

  useEffect(() => {
    if (state.kind !== "ok") return;
    const sb = createBrowserSupabase();
    let off = () => {};
    let alive = true;
    subscribeGroup(sb, state.group.id, (trip) => {
      // 알림·이동은 부수 효과라 setState 갱신 함수 밖에서 한다 (갱신 함수는 두 번 불릴 수 있다)
      const { trips: next, owedWon } = applyTripChange(tripsRef.current, trip, state.me);
      tripsRef.current = next;
      setTrips(next);
      if (owedWon !== null) {
        notifySettlement(state.group.name, owedWon);
        router.push(`/trips/${trip.id}/settle`);
      }
    }).then((u) => { if (alive) off = u; else u(); });
    return () => { alive = false; off(); };
  }, [state, router]);

  if (state.kind === "error") {
    return (
      <Screen>
        <PageHeader title="그룹" back="/" />
        <Section role="alert"><Lead>그룹을 열 수 없습니다.</Lead><Hint>이 그룹의 멤버가 아니거나 네트워크 연결이 끊겼습니다.</Hint></Section>
        <ButtonLink href="/">홈으로</ButtonLink>
      </Screen>
    );
  }

  const active = trips.find((x) => x.endedAt === null) ?? null;
  const settled = trips.find((x) => x.settlement !== null) ?? null;

  return (
    <Screen>
      <PageHeader title={state.group.name} back="/" />
      <Section aria-labelledby="trip-title">
        <SectionHead><h2 id="trip-title">운행</h2>{active ? <Tag tone="accent">운행 중</Tag> : <Tag>운행 전</Tag>}</SectionHead>
        <List>
          <Item><ItemText><strong>{active?.destinationName ?? state.group.destinationName}</strong>
            <span>{active ? "도착 후 운전자가 정산을 요청하면 이 화면에 바로 표시됩니다." : "운행이 시작되면 상태가 표시됩니다."}</span></ItemText></Item>
        </List>
        {settled && <ButtonLink href={`/trips/${settled.id}/settle`} variant="ghost">정산 내역 보기</ButtonLink>}
      </Section>

      <Section aria-labelledby="members-title">
        <SectionHead><h2 id="members-title">멤버</h2><span>{state.members.length}명</span></SectionHead>
        <List>
          {state.members.map((m) => (
            <Item key={m.id}>
              <Avatar aria-hidden="true">{m.displayName.slice(0, 1)}</Avatar>
              <ItemText><strong>{m.displayName}</strong>{m.payLink && <span>송금 링크 등록됨</span>}</ItemText>
              {m.id === state.me && <Tag>나</Tag>}
            </Item>
          ))}
        </List>
      </Section>

      {perm === "default" && (
        <Section>
          <List>
            <Item><ItemText><strong>정산 알림</strong><span>정산 요청이 오면 브라우저 알림을 받습니다.</span></ItemText>
              <Button type="button" variant="ghost" style={{ width: "auto", minHeight: 44, padding: "0 16px" }}
                onClick={async () => setPerm(await askNotificationPermission())}>켜기</Button></Item>
          </List>
        </Section>
      )}
    </Screen>
  );
}
