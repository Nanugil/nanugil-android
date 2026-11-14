"use client";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { subscribeGroup } from "@/lib/api";
import { askNotificationPermission, notifySettlement } from "@/lib/notify";
import { applyTripChange } from "@/lib/tripChange";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import type { Group, Member, Trip } from "@/lib/types";
import { Button, ButtonLink, CtaLink } from "@/ui/Button";
import { Icon } from "@/ui/Icon";
import { Body, Chip, IconBox, Initial, Label, Left, Line, PageHeader, Row, Rows, SectionHead, Stack, Tile, Where } from "@/ui/kit";
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
        <Tile role="alert">
          <Line><Left><IconBox><Icon name="error" /></IconBox><Stack><b>그룹을 열 수 없어요</b><Label>이 그룹의 멤버가 아니거나 연결이 끊겼어요.</Label></Stack></Left></Line>
        </Tile>
        <ButtonLink href="/">홈으로</ButtonLink>
      </Screen>
    );
  }

  const active = trips.find((x) => x.endedAt === null) ?? null;
  const settled = trips.find((x) => x.settlement !== null) ?? null;

  return (
    <Screen>
      <PageHeader title={state.group.name} back="/" />
      <Body>
        <Tile aria-labelledby="trip-status">
          <Line>
            <Left>
              <IconBox tone={active ? "accent" : "plain"}><Icon name={active ? "directions_car" : "local_parking"} /></IconBox>
              <Stack><b id="trip-status">{active ? "운행 중" : "운행 전이에요"}</b>
                <Where><Icon name="location_on" />{active?.destinationName ?? state.group.destinationName}</Where></Stack>
            </Left>
            {active && <Chip tone="accent"><Icon name="sensors" />실시간</Chip>}
          </Line>
          {active && <Label>도착하면 운전자가 정산 요청을 보내요. 이 화면을 열어 두면 바로 알려 드려요.</Label>}
        </Tile>
        {settled && <CtaLink href={`/trips/${settled.id}/settle`}>정산 보기</CtaLink>}
      </Body>

      <Body>
        <SectionHead>멤버<span className="num">{state.members.length}명</span></SectionHead>
        <Tile>
          <Rows>
            {state.members.map((m) => (
              <Row key={m.id}>
                <Initial aria-hidden="true">{m.displayName.slice(0, 1)}</Initial>
                <Stack style={{ flex: 1 }}><b>{m.displayName}</b></Stack>
                {m.id === state.me && <Chip tone="plain">나</Chip>}
                {m.payLink && <Label>송금 링크 있음</Label>}
              </Row>
            ))}
          </Rows>
        </Tile>
      </Body>

      {perm === "default" && (
        <Tile>
          <Line>
            <Left><IconBox><Icon name="notifications" /></IconBox><Stack><b>정산 알림 받기</b><Label>홈 화면에 추가하면 더 잘 받아요</Label></Stack></Left>
            <Button type="button" variant="ghost" style={{ width: "auto", minHeight: 44, padding: "0 14px" }}
              onClick={async () => setPerm(await askNotificationPermission())}>켜기</Button>
          </Line>
        </Tile>
      )}
    </Screen>
  );
}
