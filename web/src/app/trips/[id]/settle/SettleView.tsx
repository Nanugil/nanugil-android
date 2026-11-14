"use client";
import styled from "@emotion/styled";
import { useEffect, useRef, useState } from "react";
import { getTrip, markReceived, markSent, subscribeGroup } from "@/lib/api";
import { ServerError, messageFor } from "@/lib/errors";
import { formatWon } from "@/lib/format";
import { isKakaoPayLink } from "@/lib/payLink";
import { statusOf, viewFor, type TransferStatus } from "@/lib/settlement";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import type { Member, Settlement, Transfer, Trip } from "@/lib/types";
import { Button, ButtonA, ButtonLink } from "@/ui/Button";
import { DotNumber } from "@/ui/DotNumber";
import { Icon } from "@/ui/Icon";
import { Avatar, Flash, Hint, IconButton, Item, ItemText, Lead, List, PageHeader, Section, SectionHead, Value } from "@/ui/kit";
import { useCountUpValue, useDrawCheck, useFlashOnChange } from "@/ui/motion";
import { Screen } from "@/ui/Screen";
import { Segments } from "@/ui/Segments";
import { StatusChip } from "@/ui/StatusChip";
import { t } from "@/ui/theme";

type State = { kind: "error" } | { kind: "ok"; trip: Trip; members: Member[]; me: string };

const Amount = styled.div`display: flex; align-items: flex-end; gap: 6px;
  > span[aria-hidden] { font-size: 20px; font-weight: 700; line-height: 1; }`;
const Err = styled.p`font-size: 13px; color: ${t.color.error};`;
const Done = styled.p`display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 500; color: ${t.color.success};`;
const Tick = styled.svg`width: 20px; height: 20px; flex: none;`;
const Actions = styled.div`display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px;`;
const Meta = styled.div`display: flex; justify-content: space-between; gap: 12px;`;

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
    <Item>
      <Flash ref={flash} />
      <Avatar aria-hidden="true">{from.slice(0, 1)}</Avatar>
      <ItemText><strong>{from}</strong><span>{to}에게 {formatWon(transfer.amountWon)}</span></ItemText>
      <StatusChip status={status} />
    </Item>
  );
}

function AmountSection({ label, amountWon, perPersonWon, done, total }: { label: string; amountWon: number; perPersonWon: number; done: number; total: number }) {
  const shown = useCountUpValue(amountWon);
  return (
    <Section aria-labelledby="hero-title">
      <SectionHead><h2 id="hero-title">{label}</h2><span>1인 {formatWon(perPersonWon)}</span></SectionHead>
      <Amount>
        <span className="visually-hidden">{formatWon(amountWon)}</span>
        <DotNumber text={new Intl.NumberFormat("ko-KR").format(shown)} height={44} /><span aria-hidden="true">원</span>
      </Amount>
      <Segments done={done} total={total} label={`그룹 송금 ${total}건 중 ${done}건 완료`} />
      <Meta><Hint>그룹 송금</Hint><Hint className="num">{done}/{total} 완료</Hint></Meta>
    </Section>
  );
}

/** 공유 시트나 클립보드로 링크를 넘긴다. 사용자가 취소했거나 실패하면 false */
async function shareLink(): Promise<boolean> {
  const url = window.location.href;
  try {
    if (navigator.share) { await navigator.share({ title: "나누길 정산", url }); return true; }
    await navigator.clipboard.writeText(url);
    return true;
  } catch { return false; }
}

export function SettleView({ state }: { state: State }) {
  const [settlement, setSettlement] = useState<Settlement | null>(state.kind === "ok" ? state.trip.settlement : null);
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [announce, setAnnounce] = useState("");
  const inFlight = useRef(new Set<number>());

  useEffect(() => {
    if (state.kind !== "ok") return;
    let off = () => {};
    let alive = true;
    subscribeGroup(createBrowserSupabase(), state.trip.groupId, (trip) => {
      if (trip.id === state.trip.id) setSettlement(trip.settlement);
    }, (status) => {
      if (status === "SUBSCRIBED") getTrip(createBrowserSupabase(), state.trip.id).then((tr) => setSettlement(tr.settlement)).catch(() => {});
    }).then((u) => { if (alive) off = u; else u(); });
    return () => { alive = false; off(); };
  }, [state]);

  if (state.kind === "error") {
    return (
      <Screen>
        <PageHeader title="정산" back="/" />
        <Section role="alert"><Lead>정산 정보를 열 수 없습니다.</Lead><Hint>이 그룹의 멤버가 아니거나 네트워크 연결이 끊겼습니다.</Hint></Section>
        <ButtonLink href="/">홈으로</ButtonLink>
      </Screen>
    );
  }

  const names = new Map(state.members.map((m) => [m.id, m.displayName]));
  const payLinks = new Map(state.members.map((m) => [m.id, m.payLink]));
  const nameOf = (id: string) => names.get(id) ?? "이름 없음";
  const groupHref = `/groups/${state.trip.groupId}`;

  if (!settlement) {
    return (
      <Screen>
        <PageHeader title="정산" back={groupHref} />
        <Section><Lead>아직 정산 요청이 없습니다.</Lead><Hint>도착 후 운전자가 정산을 요청하면 금액이 표시됩니다.</Hint></Section>
        <ButtonLink href={groupHref} variant="ghost">그룹으로</ButtonLink>
      </Screen>
    );
  }

  const view = viewFor(settlement, state.me);
  const doneCount = settlement.transfers.filter((x) => statusOf(x) === "received").length;
  const pendingSend = view.toSend.filter((x) => statusOf(x.transfer) === "pending");
  const sendTotal = pendingSend.reduce((s, x) => s + x.transfer.amountWon, 0);
  const sentTotal = view.toSend.reduce((s, x) => s + x.transfer.amountWon, 0);
  const receiveTotal = view.toReceive.reduce((s, x) => s + x.transfer.amountWon, 0);

  async function mark(index: number, side: "sent" | "received") {
    if (state.kind !== "ok" || !settlement || inFlight.current.has(index)) return;
    inFlight.current.add(index);
    setBusy(index); setError(null);
    const sb = createBrowserSupabase();
    try {
      setSettlement(side === "sent" ? await markSent(sb, state.trip.id, index, settlement.requestedAt)
        : await markReceived(sb, state.trip.id, index, settlement.requestedAt));
      setAnnounce(side === "sent" ? "송금 완료로 표시했습니다." : "수령을 확인했습니다.");
    } catch (e) {
      const code = e instanceof ServerError ? e.code : "UNKNOWN";
      setError(messageFor(code));
      if (code === "SETTLEMENT_CHANGED") setSettlement((await getTrip(sb, state.trip.id).catch(() => null))?.settlement ?? settlement);
    } finally { inFlight.current.delete(index); setBusy(null); }
  }

  const share = (
    <IconButton type="button" aria-label={copied ? "링크 공유됨" : "링크 공유"} onClick={async () => { if (await shareLink()) { setCopied(true); setTimeout(() => setCopied(false), 3000); } }}>
      <Icon name={copied ? "check" : "ios_share"} />
    </IconButton>
  );

  return (
    <Screen nav={false}>
      <PageHeader title="정산" back={groupHref} action={share} />
      <AmountSection
        label={pendingSend.length > 0 ? "보낼 금액" : view.toSend.length > 0 ? "보낸 금액" : view.toReceive.length > 0 ? "받을 금액" : "1인 부담액"}
        amountWon={pendingSend.length > 0 ? sendTotal : view.toSend.length > 0 ? sentTotal : view.toReceive.length > 0 ? receiveTotal : view.perPersonWon}
        perPersonWon={view.perPersonWon} done={doneCount} total={settlement.transfers.length} />
      <p role="status" className="visually-hidden">{announce}</p>

      {view.toSend.length > 0 && (
        <Section aria-labelledby="send-title">
          <SectionHead><h2 id="send-title">받는 사람</h2></SectionHead>
          {view.toSend.map(({ index, transfer }) => {
            const st = statusOf(transfer);
            const link = payLinks.get(transfer.to) ?? null;
            return (
              <div key={index} style={{ display: "grid", gap: 12 }}>
                <List><Item>
                  <Avatar aria-hidden="true">{nameOf(transfer.to).slice(0, 1)}</Avatar>
                  <ItemText><strong>{nameOf(transfer.to)}</strong><span>운전자</span></ItemText>
                  <Value>{formatWon(transfer.amountWon)}</Value>
                </Item></List>
                {st !== "pending" ? (
                  <Done><CheckMark on />{st === "received" ? "운전자가 수령을 확인했습니다." : "송금 완료로 표시했습니다. 운전자의 확인을 기다리고 있습니다."}</Done>
                ) : (
                  <>
                    <Actions>
                      {isKakaoPayLink(link)
                        ? <ButtonA href={link!} target="_blank" rel="noopener noreferrer">카카오페이 송금</ButtonA>
                        : <Button type="button" disabled>카카오페이 송금</Button>}
                      <Button type="button" variant="ghost" onClick={() => mark(index, "sent")} disabled={busy === index} aria-busy={busy === index}>
                        {busy === index ? "처리 중" : "송금 완료 표시"}
                      </Button>
                    </Actions>
                    {!isKakaoPayLink(link) && <Hint>운전자가 송금 링크를 등록하지 않았습니다. 직접 송금한 뒤 “송금 완료 표시”를 눌러 주세요.</Hint>}
                  </>
                )}
              </div>
            );
          })}
          {error && <Err role="alert">{error}</Err>}
        </Section>
      )}

      {view.toSend.length === 0 && view.toReceive.length === 0 && (
        <Section><Lead>정산할 금액이 없습니다.</Lead><Hint>이번 운행에서 보내거나 받을 금액이 없습니다.</Hint></Section>
      )}

      {view.toReceive.length > 0 && (
        <Section aria-labelledby="receive-title">
          <SectionHead><h2 id="receive-title">보낼 사람</h2><span className="num">{view.toReceive.length}명</span></SectionHead>
          <List>
            {view.toReceive.map(({ index, transfer }) => (
              <Item key={index}>
                <Avatar aria-hidden="true">{nameOf(transfer.from).slice(0, 1)}</Avatar>
                <ItemText><strong>{nameOf(transfer.from)}</strong><span>{formatWon(transfer.amountWon)}</span></ItemText>
                {statusOf(transfer) === "received" ? <StatusChip status="received" /> : (
                  <Button type="button" variant="ghost" style={{ width: "auto", minHeight: 44, padding: "0 14px", fontSize: 14 }}
                    onClick={() => mark(index, "received")} disabled={busy === index} aria-busy={busy === index}>수령 확인</Button>
                )}
              </Item>
            ))}
          </List>
          {view.toSend.length === 0 && error && <Err role="alert">{error}</Err>}
        </Section>
      )}

      <Section aria-labelledby="ledger-title">
        <SectionHead><h2 id="ledger-title">{view.allDone ? "정산 완료" : "송금 현황"}</h2><span>{doneCount}/{settlement.transfers.length}</span></SectionHead>
        <List>
          {settlement.transfers.map((tr, i) => <LedgerRow key={i} from={nameOf(tr.from)} to={nameOf(tr.to)} transfer={tr} />)}
        </List>
      </Section>
    </Screen>
  );
}
