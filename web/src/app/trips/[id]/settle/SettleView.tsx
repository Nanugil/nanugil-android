"use client";
import styled from "@emotion/styled";
import { useEffect, useRef, useState } from "react";
import { markReceived, markSent, subscribeGroup } from "@/lib/api";
import { ServerError, messageFor } from "@/lib/errors";
import { formatWon } from "@/lib/format";
import { isKakaoPayLink } from "@/lib/payLink";
import { statusOf, viewFor, type TransferStatus } from "@/lib/settlement";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import type { Member, Settlement, Transfer, Trip } from "@/lib/types";
import { AmountCard } from "@/ui/AmountCard";
import { Button, ButtonA, ButtonLink } from "@/ui/Button";
import { GlassCard } from "@/ui/GlassCard";
import { Icon } from "@/ui/Icon";
import { Flash, MemberRow, Rows } from "@/ui/MemberRow";
import { useDrawCheck, useFlashOnChange } from "@/ui/motion";
import { Back, Screen } from "@/ui/Screen";
import { StatusChip } from "@/ui/StatusChip";
import { t } from "@/ui/theme";

type State = { kind: "error" } | { kind: "ok"; trip: Trip; members: Member[]; me: string };

const Title = styled.h1`font-size: ${t.text.x2};`;
const H2 = styled.h2`font-size: ${t.text.lg};`;
const Lead = styled.p`color: ${t.color.ink2};`;
const To = styled.p`display: flex; align-items: baseline; justify-content: space-between; gap: ${t.space.sm};
  font-size: ${t.text.lg}; font-weight: 600;`;
const ToAmount = styled.span`font-variant-numeric: tabular-nums;`;
const Muted = styled.p`font-size: ${t.text.sm}; color: ${t.color.muted};`;
const Err = styled.p`font-size: ${t.text.sm}; color: ${t.color.error}; min-height: 1lh;`;
const Done = styled.p`display: flex; align-items: center; gap: ${t.space.xs}; font-weight: 600; color: ${t.color.success};`;
const Share = styled.button`display: inline-flex; align-items: center; gap: ${t.space.x2s}; min-height: 44px; padding: 0 ${t.space.sm};
  border: 1px solid ${t.color.rule}; border-radius: ${t.radius.control}; background: transparent; cursor: pointer; font-size: ${t.text.sm};
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }
  &:active { transform: scale(0.98); }`;
const Tick = styled.svg`width: 22px; height: 22px; flex: none;`;

function CheckMark({ on }: { on: boolean }) {
  const path = useRef<SVGPathElement>(null);
  useDrawCheck(path, on);
  if (!on) return null;
  return (
    <Tick viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="11" fill="var(--color-success)" />
      <path ref={path} d="M7 12.5l3.2 3.2L17 9" fill="none" stroke="var(--color-accent-ink)" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
    </Tick>
  );
}

function LedgerRow({ from, to, transfer }: { from: string; to: string; transfer: Transfer }) {
  const status: TransferStatus = statusOf(transfer);
  const flash = useRef<HTMLSpanElement>(null);
  useFlashOnChange(flash, status);
  return (
    <MemberRow name={from} sub={`${to}에게 ${formatWon(transfer.amountWon)}`} trailing={<StatusChip status={status} />}>
      <Flash ref={flash} />
    </MemberRow>
  );
}

async function shareLink() {
  const url = window.location.href;
  if (navigator.share) { await navigator.share({ title: "나누길 정산", url }).catch(() => {}); return; }
  await navigator.clipboard?.writeText(url);
}

export function SettleView({ state }: { state: State }) {
  const [settlement, setSettlement] = useState<Settlement | null>(state.kind === "ok" ? state.trip.settlement : null);
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (state.kind !== "ok") return;
    let off = () => {};
    let alive = true;
    subscribeGroup(createBrowserSupabase(), state.trip.groupId, (trip) => {
      if (trip.id === state.trip.id) setSettlement(trip.settlement);
    }).then((u) => { if (alive) off = u; else u(); });
    return () => { alive = false; off(); };
  }, [state]);

  if (state.kind === "error") {
    return (
      <Screen>
        <GlassCard role="alert">
          <Title>정산을 열 수 없어요</Title>
          <Lead>이 그룹의 멤버가 아니거나 연결이 끊겼어요.</Lead>
          <ButtonLink href="/">홈으로</ButtonLink>
        </GlassCard>
      </Screen>
    );
  }

  const names = new Map(state.members.map((m) => [m.id, m.displayName]));
  const payLinks = new Map(state.members.map((m) => [m.id, m.payLink]));
  const nameOf = (id: string) => names.get(id) ?? "알 수 없음";
  const back = <Back href={`/groups/${state.trip.groupId}`} label="그룹" />;

  if (!settlement) {
    return (
      <Screen>
        {back}
        <GlassCard>
          <Title>아직 정산 요청 전이에요</Title>
          <Lead>도착한 뒤 운전자가 요청을 보내면 여기에 금액이 나와요.</Lead>
          <ButtonLink href={`/groups/${state.trip.groupId}`} variant="ghost">그룹으로</ButtonLink>
        </GlassCard>
      </Screen>
    );
  }

  const view = viewFor(settlement, state.me);

  async function mark(index: number, side: "sent" | "received") {
    setBusy(index); setError(null);
    try {
      const sb = createBrowserSupabase();
      setSettlement(side === "sent" ? await markSent(sb, state.kind === "ok" ? state.trip.id : "", index)
        : await markReceived(sb, state.kind === "ok" ? state.trip.id : "", index));
    } catch (e) {
      setError(messageFor(e instanceof ServerError ? e.code : "UNKNOWN"));
    } finally { setBusy(null); }
  }

  const share = (
    <Share type="button" onClick={async () => { await shareLink(); setCopied(true); }}>
      <Icon name="ios_share" />{copied ? "링크를 담았어요" : "카톡으로 공유"}
    </Share>
  );

  return (
    <Screen action={share}>
      {back}
      <AmountCard label="1인 부담액" amountWon={view.perPersonWon} sub={state.trip.destinationName} />

      {view.toSend.map(({ index, transfer }) => {
        const sent = statusOf(transfer) !== "pending";
        const link = payLinks.get(transfer.to) ?? null;
        return (
          <GlassCard key={index} aria-label={`${nameOf(transfer.to)}에게 보내기`}>
            <To>{nameOf(transfer.to)}에게<ToAmount>{formatWon(transfer.amountWon)}</ToAmount></To>
            {sent ? (
              <Done role="status"><CheckMark on={sent} />{statusOf(transfer) === "received" ? "운전자가 받았다고 확인했어요" : "보냈다고 알렸어요"}</Done>
            ) : (
              <>
                {isKakaoPayLink(link) ? (
                  <ButtonA href={link!} target="_blank" rel="noopener noreferrer"><Icon name="payments" />카카오페이로 보내기</ButtonA>
                ) : (
                  <>
                    <Button type="button" disabled><Icon name="payments" />카카오페이로 보내기</Button>
                    <Muted>운전자가 아직 송금 링크를 등록하지 않았어요.</Muted>
                  </>
                )}
                <Button type="button" variant="ghost" onClick={() => mark(index, "sent")} disabled={busy === index} aria-busy={busy === index}>
                  <Icon name="check" />보냈어요
                </Button>
              </>
            )}
          </GlassCard>
        );
      })}

      {view.toReceive.length > 0 && (
        <GlassCard aria-labelledby="receive">
          <H2 id="receive">받을 돈</H2>
          <Rows>
            {view.toReceive.map(({ index, transfer }) => (
              <MemberRow key={index} name={nameOf(transfer.from)} sub={formatWon(transfer.amountWon)}
                trailing={statusOf(transfer) === "received" ? <StatusChip status="received" /> : (
                  <Button type="button" variant="ghost" style={{ width: "auto", minHeight: 40 }}
                    onClick={() => mark(index, "received")} disabled={busy === index} aria-busy={busy === index}>받았어요</Button>
                )} />
            ))}
          </Rows>
        </GlassCard>
      )}

      {view.toSend.length === 0 && view.toReceive.length === 0 && (
        <GlassCard><H2>보낼 돈이 없어요</H2><Lead>이번 운행에서 내가 보낼 송금은 없어요.</Lead></GlassCard>
      )}
      {error && <Err role="alert">{error}</Err>}

      <GlassCard aria-labelledby="ledger">
        <H2 id="ledger">{view.allDone ? "정산이 모두 끝났어요" : "그룹 송금 현황"}</H2>
        <Rows>
          {settlement.transfers.map((tr, i) => <LedgerRow key={i} from={nameOf(tr.from)} to={nameOf(tr.to)} transfer={tr} />)}
        </Rows>
      </GlassCard>
    </Screen>
  );
}
