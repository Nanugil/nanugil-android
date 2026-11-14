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
import { Big, Body, Flash, IconBox, Initial, Label, Left, Line, PageHeader, RoundButton, Row, Rows, SectionHead, Stack, Tile, Where } from "@/ui/kit";
import { useCountUpValue, useDrawCheck, useFlashOnChange, useRiseIn } from "@/ui/motion";
import { Screen } from "@/ui/Screen";
import { Segments } from "@/ui/Segments";
import { StatusChip } from "@/ui/StatusChip";
import { t } from "@/ui/theme";

type State = { kind: "error" } | { kind: "ok"; trip: Trip; members: Member[]; me: string };

const Amount = styled.div`display: flex; align-items: flex-end; gap: 3px; flex: none;
  span { font-size: ${t.text.sm}; font-weight: 700; line-height: 1; }`;
const Gauge = styled.div`display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 10px;
  > div div span { height: 12px; border-radius: 4px; }`;
const Err = styled.p`font-size: ${t.text.sm}; color: ${t.color.error};`;
const Done = styled.p`display: flex; align-items: center; gap: ${t.space.xs}; font-size: ${t.text.sm}; font-weight: 600; color: ${t.color.success};`;
const Tick = styled.svg`width: 22px; height: 22px; flex: none;`;
const Actions = styled.div`display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: ${t.space.xs};`;

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
    <Row>
      <Flash ref={flash} />
      <Initial aria-hidden="true">{from.slice(0, 1)}</Initial>
      <Stack style={{ flex: 1 }}><b>{from}</b><Label>{to}에게 {formatWon(transfer.amountWon)}</Label></Stack>
      <StatusChip status={status} />
    </Row>
  );
}

function AmountTile({ amountWon, sub, done, total }: { amountWon: number; sub: string; done: number; total: number }) {
  const ref = useRef<HTMLElement>(null);
  useRiseIn(ref);
  const shown = useCountUpValue(amountWon);
  return (
    <Tile ref={ref} pad="md" aria-label={`1인 부담액 ${formatWon(amountWon)}`}>
      <Line>
        <Stack><Label>1인 부담액</Label><Where><Icon name="location_on" />{sub}</Where></Stack>
        <Amount aria-hidden="true"><DotNumber text={new Intl.NumberFormat("ko-KR").format(shown)} height={36} /><span>원</span></Amount>
      </Line>
      <Gauge>
        <div><Segments done={done} total={total} label={`송금 ${total}건 중 ${done}건 완료`} /></div>
        <Label className="num">{done}/{total} 완료</Label>
      </Gauge>
    </Tile>
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
        <PageHeader title="정산" back="/" />
        <Tile role="alert"><Line><Left><IconBox><Icon name="error" /></IconBox>
          <Stack><b>정산을 열 수 없어요</b><Label>이 그룹의 멤버가 아니거나 연결이 끊겼어요.</Label></Stack></Left></Line></Tile>
        <ButtonLink href="/">홈으로</ButtonLink>
      </Screen>
    );
  }

  const names = new Map(state.members.map((m) => [m.id, m.displayName]));
  const payLinks = new Map(state.members.map((m) => [m.id, m.payLink]));
  const nameOf = (id: string) => names.get(id) ?? "알 수 없음";
  const groupHref = `/groups/${state.trip.groupId}`;

  if (!settlement) {
    return (
      <Screen>
        <PageHeader title="정산" back={groupHref} />
        <Tile><Line><Left><IconBox><Icon name="hourglass_top" /></IconBox>
          <Stack><b>아직 정산 요청 전이에요</b><Label>도착한 뒤 운전자가 요청을 보내면 여기에 금액이 나와요.</Label></Stack></Left></Line></Tile>
        <ButtonLink href={groupHref} variant="ghost">그룹으로</ButtonLink>
      </Screen>
    );
  }

  const view = viewFor(settlement, state.me);
  const doneCount = settlement.transfers.filter((x) => statusOf(x) === "received").length;

  async function mark(index: number, side: "sent" | "received") {
    if (state.kind !== "ok" || !settlement) return;
    setBusy(index); setError(null);
    const sb = createBrowserSupabase();
    try {
      setSettlement(side === "sent" ? await markSent(sb, state.trip.id, index, settlement.requestedAt)
        : await markReceived(sb, state.trip.id, index, settlement.requestedAt));
    } catch (e) {
      const code = e instanceof ServerError ? e.code : "UNKNOWN";
      setError(messageFor(code));
      if (code === "SETTLEMENT_CHANGED") setSettlement((await getTrip(sb, state.trip.id).catch(() => null))?.settlement ?? settlement);
    } finally { setBusy(null); }
  }

  const share = (
    <RoundButton type="button" aria-label={copied ? "링크를 담았어요" : "카톡으로 공유"} onClick={async () => { await shareLink(); setCopied(true); }}>
      <Icon name={copied ? "check" : "ios_share"} />
    </RoundButton>
  );

  return (
    <Screen>
      <PageHeader title="정산" back={groupHref} action={share} />
      <Body>
        <AmountTile amountWon={view.perPersonWon} sub={state.trip.destinationName} done={doneCount} total={settlement.transfers.length} />

        {view.toSend.map(({ index, transfer }) => {
          const st = statusOf(transfer);
          const link = payLinks.get(transfer.to) ?? null;
          return (
            <Tile key={index} aria-label={`${nameOf(transfer.to)}에게 보내기`}>
              <Line>
                <Left><Initial aria-hidden="true">{nameOf(transfer.to).slice(0, 1)}</Initial>
                  <Stack><Label>보낼 사람</Label><b>{nameOf(transfer.to)}</b></Stack></Left>
                <Big className="num">{formatWon(transfer.amountWon)}</Big>
              </Line>
              {st !== "pending" ? (
                <Done role="status"><CheckMark on />{st === "received" ? "운전자가 받았다고 확인했어요" : "보냈다고 알렸어요"}</Done>
              ) : (
                <>
                  <Actions>
                    {isKakaoPayLink(link)
                      ? <ButtonA href={link!} target="_blank" rel="noopener noreferrer"><Icon name="payments" />카카오페이</ButtonA>
                      : <Button type="button" disabled><Icon name="payments" />카카오페이</Button>}
                    <Button type="button" variant="ghost" onClick={() => mark(index, "sent")} disabled={busy === index} aria-busy={busy === index}>
                      <Icon name="check" />보냈어요
                    </Button>
                  </Actions>
                  {!isKakaoPayLink(link) && <Label>운전자가 아직 송금 링크를 등록하지 않았어요. 직접 보낸 뒤 보냈어요를 눌러 주세요.</Label>}
                </>
              )}
            </Tile>
          );
        })}

        {view.toSend.length === 0 && view.toReceive.length === 0 && (
          <Tile><Line><Left><IconBox tone="success"><Icon name="check" /></IconBox>
            <Stack><b>보낼 돈이 없어요</b><Label>이번 운행에서 내가 보낼 송금은 없어요.</Label></Stack></Left></Line></Tile>
        )}
        {error && <Err role="alert">{error}</Err>}
      </Body>

      {view.toReceive.length > 0 && (
        <Body>
          <SectionHead>받을 돈<span className="num">{formatWon(view.toReceive.reduce((s, x) => s + x.transfer.amountWon, 0))}</span></SectionHead>
          <Tile>
            <Rows>
              {view.toReceive.map(({ index, transfer }) => (
                <Row key={index}>
                  <Initial aria-hidden="true">{nameOf(transfer.from).slice(0, 1)}</Initial>
                  <Stack style={{ flex: 1 }}><b>{nameOf(transfer.from)}</b><Label className="num">{formatWon(transfer.amountWon)}</Label></Stack>
                  {statusOf(transfer) === "received" ? <StatusChip status="received" /> : (
                    <Button type="button" variant={statusOf(transfer) === "sent" ? "primary" : "ghost"} style={{ width: "auto", minHeight: 44, padding: "0 14px" }}
                      onClick={() => mark(index, "received")} disabled={busy === index} aria-busy={busy === index}>받았어요</Button>
                  )}
                </Row>
              ))}
            </Rows>
          </Tile>
        </Body>
      )}

      <Body>
        <SectionHead>{view.allDone ? "정산이 모두 끝났어요" : "그룹 송금 현황"}<span className="num">{doneCount}/{settlement.transfers.length}</span></SectionHead>
        <Tile>
          <Rows>
            {settlement.transfers.map((tr, i) => <LedgerRow key={i} from={nameOf(tr.from)} to={nameOf(tr.to)} transfer={tr} />)}
          </Rows>
        </Tile>
      </Body>
    </Screen>
  );
}
