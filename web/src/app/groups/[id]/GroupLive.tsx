"use client";
import styled from "@emotion/styled";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { subscribeGroup } from "@/lib/api";
import { askNotificationPermission, notifySettlement } from "@/lib/notify";
import { viewFor } from "@/lib/settlement";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import type { Group, Member, Trip } from "@/lib/types";
import { Button, ButtonLink } from "@/ui/Button";
import { GlassCard } from "@/ui/GlassCard";
import { Icon } from "@/ui/Icon";
import { MemberRow, Rows } from "@/ui/MemberRow";
import { Back, Screen } from "@/ui/Screen";
import { t } from "@/ui/theme";

type State = { kind: "error" } | { kind: "ok"; group: Group; members: Member[]; trips: Trip[]; me: string };

const Title = styled.h1`font-size: ${t.text.x2};`;
const H2 = styled.h2`font-size: ${t.text.lg};`;
const Lead = styled.p`color: ${t.color.ink2};`;
const Status = styled.p`display: flex; align-items: center; gap: ${t.space.xs}; font-weight: 600;
  .material-symbols-rounded { color: ${t.color.accent}; }`;
const Where = styled.p`display: flex; align-items: center; gap: ${t.space.x2s}; color: ${t.color.ink2};
  .material-symbols-rounded { color: ${t.color.warn}; }`;
const Me = styled.span`font-size: ${t.text.xs}; color: ${t.color.muted};`;
const Hint = styled.p`font-size: ${t.text.xs}; color: ${t.color.muted};`;

export function GroupLive({ state }: { state: State }) {
  const router = useRouter();
  const [trips, setTrips] = useState(state.kind === "ok" ? state.trips : []);
  const [perm, setPerm] = useState<NotificationPermission | "unsupported">("granted");

  useEffect(() => { setPerm(typeof Notification === "undefined" ? "unsupported" : Notification.permission); }, []);

  useEffect(() => {
    if (state.kind !== "ok") return;
    const sb = createBrowserSupabase();
    let off = () => {};
    let alive = true;
    subscribeGroup(sb, state.group.id, (trip) => {
      setTrips((prev) => {
        const old = prev.find((x) => x.id === trip.id);
        // 정산 요청이 새로 오거나 다시 오고, 나에게 보낼 송금이 있으면 알리고 정산 화면으로
        if (trip.settlement && trip.settlement.requestedAt !== old?.settlement?.requestedAt) {
          const toSend = viewFor(trip.settlement, state.me).toSend;
          if (toSend.length > 0) {
            notifySettlement(state.group.name, toSend.reduce((s, x) => s + x.transfer.amountWon, 0));
            router.push(`/trips/${trip.id}/settle`);
          }
        }
        return old ? prev.map((x) => (x.id === trip.id ? trip : x)) : [trip, ...prev];
      });
    }).then((u) => { if (alive) off = u; else u(); });
    return () => { alive = false; off(); };
  }, [state, router]);

  if (state.kind === "error") {
    return (
      <Screen>
        <GlassCard role="alert">
          <Title>그룹을 열 수 없어요</Title>
          <Lead>이 그룹의 멤버가 아니거나 연결이 끊겼어요.</Lead>
          <ButtonLink href="/">홈으로</ButtonLink>
        </GlassCard>
      </Screen>
    );
  }

  const active = trips.find((x) => x.endedAt === null) ?? null;
  const settled = trips.find((x) => x.settlement !== null) ?? null;

  return (
    <Screen>
      <Back href="/" label="내 그룹" />
      <Title>{state.group.name}</Title>
      <GlassCard aria-labelledby="trip-status">
        {active ? (
          <>
            <Status id="trip-status"><Icon name="directions_car" />운행 중</Status>
            <Where><Icon name="location_on" />{active.destinationName}</Where>
            <Lead>도착하면 운전자가 정산 요청을 보내요. 이 화면을 열어 두면 바로 알려 드려요.</Lead>
          </>
        ) : (
          <>
            <Status id="trip-status"><Icon name="local_parking" />지금은 운행 중이 아니에요</Status>
            <Where><Icon name="location_on" />{state.group.destinationName}</Where>
          </>
        )}
        {settled && <ButtonLink href={`/trips/${settled.id}/settle`} variant={active ? "ghost" : "primary"}>정산 보기</ButtonLink>}
      </GlassCard>
      <GlassCard aria-labelledby="members">
        <H2 id="members">멤버 {state.members.length}명</H2>
        <Rows>
          {state.members.map((m) => (
            <MemberRow key={m.id} name={m.displayName} trailing={m.id === state.me ? <Me>나</Me> : undefined} />
          ))}
        </Rows>
      </GlassCard>
      {perm === "default" && (
        <GlassCard>
          <Lead>정산 요청이 오면 알림으로 알려 드릴게요.</Lead>
          <Button type="button" variant="ghost" onClick={async () => setPerm(await askNotificationPermission())}>
            <Icon name="notifications" />알림 받기
          </Button>
          <Hint>휴대폰 홈 화면에 추가하면 알림을 더 잘 받아요.</Hint>
        </GlassCard>
      )}
    </Screen>
  );
}
