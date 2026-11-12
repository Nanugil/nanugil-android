"use client";
import styled from "@emotion/styled";
import Link from "next/link";
import { formatWon } from "@/lib/format";
import { ButtonLink, CtaLink } from "@/ui/Button";
import { DotNumber } from "@/ui/DotNumber";
import { Icon } from "@/ui/Icon";
import { Screen } from "@/ui/Screen";
import { Segments } from "@/ui/Segments";
import { styleOnly } from "@/ui/styleProps";
import { t } from "@/ui/theme";

export type HomeGroup = {
  id: string; name: string; destination: string; driving: boolean;
  owed: { tripId: string; amountWon: number } | null;
  progress: { done: number; total: number } | null;
};

const Hello = styled.header`display: flex; align-items: center; gap: ${t.space.sm}; padding-top: ${t.space.xs};`;
const Avatar = styled.span`display: grid; place-items: center; flex: none; width: 48px; height: 48px; border-radius: 14px;
  background: ${t.color.cta}; color: ${t.color.ctaInk}; font-weight: 700; font-size: ${t.text.lg};`;
const HelloText = styled.div`display: grid; line-height: 1.25;`;
const Small = styled.span`font-size: ${t.text.sm}; color: ${t.color.muted};`;
const Name = styled.h1`font-size: ${t.text.xl};`;

// 벤토: 휴대폰은 한 줄, 넓은 화면은 두 칸
const Bento = styled.div`display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: ${t.space.sm};
  @media (min-width: 48rem) { grid-template-columns: repeat(4, minmax(0, 1fr)); }`;
const Tile = styled("section", styleOnly("span", "wideSpan"))<{ span?: number; wideSpan?: number }>`
  grid-column: span ${(p) => p.span ?? 1};
  @media (min-width: 48rem) { grid-column: span ${(p) => p.wideSpan ?? p.span ?? 1}; }
  display: grid; gap: ${t.space.sm}; align-content: start; min-width: 0;
  padding: ${t.space.md} ${t.space.lg}; border-radius: ${t.radius.card};
  background: ${t.color.card}; border: 1px solid ${t.color.rule};
`;
const TileHead = styled.div`display: flex; align-items: flex-start; justify-content: space-between; gap: ${t.space.sm};`;
const Label = styled.p`font-size: ${t.text.sm}; color: ${t.color.muted}; line-height: 1.3;`;
const Strong = styled.p`font-size: ${t.text.md}; font-weight: 600; line-height: 1.3;`;
const IconBox = styled.span`display: grid; place-items: center; flex: none; width: 40px; height: 40px; border-radius: 12px;
  background: ${t.color.cardStrong}; border: 1px solid ${t.color.rule}; color: ${t.color.ink};`;
const Big = styled.p`font-size: ${t.text.x2}; font-weight: 700; line-height: 1; font-variant-numeric: tabular-nums;
  small { font-size: ${t.text.sm}; font-weight: 500; color: ${t.color.muted}; margin-left: 2px; }`;
const Chip = styled("span", styleOnly("tone"))<{ tone: "accent" | "plain" }>`display: inline-flex; align-items: center; gap: ${t.space.x2s};
  min-height: 32px; padding: 0 ${t.space.sm}; border-radius: ${t.radius.pill}; white-space: nowrap;
  font-size: ${t.text.xs}; font-weight: 600;
  border: 1px solid ${(p) => (p.tone === "accent" ? t.color.accent : t.color.rule)};
  color: ${(p) => (p.tone === "accent" ? t.color.accent : t.color.ink2)};
  background: ${(p) => (p.tone === "accent" ? "color-mix(in oklab, var(--color-accent) 8%, var(--color-card))" : t.color.card)};`;
const Amount = styled.div`display: flex; align-items: flex-end; gap: ${t.space.x2s}; color: ${t.color.ink};
  span { font-size: ${t.text.lg}; font-weight: 700; line-height: 1; }`;
const Where = styled.span`display: inline-flex; align-items: center; gap: 2px; font-size: ${t.text.sm}; color: ${t.color.ink2};
  .material-symbols-rounded { color: ${t.color.warn}; font-size: 18px; }`;
const SectionHead = styled.h2`font-size: ${t.text.lg}; padding-top: ${t.space.xs};`;
const Rows = styled.ul`list-style: none; margin: 0; padding: 0; display: grid;`;
const GroupRow = styled(Link)`display: flex; align-items: center; gap: ${t.space.sm}; min-height: 64px; text-decoration: none;
  border-bottom: 1px solid ${t.color.rule}; li:last-child & { border-bottom: 0; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }`;
const RowText = styled.span`display: grid; flex: 1; min-width: 0; line-height: 1.35;
  b { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`;
const Empty = styled.div`display: grid; gap: ${t.space.md}; justify-items: start;`;

function Greeting({ name }: { name: string }) {
  return (
    <Hello>
      <Avatar aria-hidden="true">{name ? name.slice(0, 1) : <Icon name="person" />}</Avatar>
      <HelloText><Small>안녕하세요,</Small><Name>{name || "나누길"}</Name></HelloText>
    </Hello>
  );
}

/** 홈. groups가 null이면 불러오기 실패, 빈 배열이면 아직 참여한 그룹이 없다. */
export function HomeView({ name, groups }: { name: string; groups: HomeGroup[] | null }) {
  if (groups === null || groups.length === 0) {
    const failed = groups === null;
    return (
      <Screen bar={false}>
        <Greeting name={name} />
        <Tile role={failed ? "alert" : undefined}>
          <Empty>
            <IconBox><Icon name={failed ? "wifi_off" : "qr_code_scanner"} /></IconBox>
            <Strong>{failed ? "그룹을 불러오지 못했어요" : "아직 참여한 그룹이 없어요"}</Strong>
            <Label>{failed ? "연결을 확인하고 다시 열어 주세요." : "운전자 화면의 초대 코드나 QR로 들어오면 여기서 정산 요청을 받아요."}</Label>
          </Empty>
        </Tile>
        {failed ? <ButtonLink href="/">다시 불러오기</ButtonLink> : <CtaLink href="/join">초대 코드로 참여</CtaLink>}
      </Screen>
    );
  }

  const owed = groups.find((g) => g.owed);
  const driving = groups.find((g) => g.driving);
  const settling = groups.filter((g) => g.progress && g.progress.done < g.progress.total);
  const prog = (owed ?? settling[0])?.progress ?? null;

  return (
    <Screen bar={false} wide>
      <Greeting name={name} />
      <Bento>
        <Tile span={2} aria-labelledby="owed">
          <TileHead>
            <div style={{ minWidth: 0 }}><Label id="owed">{owed ? "보낼 돈" : "정산"}</Label><Strong>{owed ? owed.name : "보낼 돈이 없어요"}</Strong></div>
            <IconBox><Icon name={owed ? "payments" : "check"} /></IconBox>
          </TileHead>
          {owed && <Amount aria-label={formatWon(owed.owed!.amountWon)}>
            <DotNumber text={new Intl.NumberFormat("ko-KR").format(owed.owed!.amountWon)} height={52} /><span aria-hidden="true">원</span>
          </Amount>}
          {prog && <><Segments done={prog.done} total={prog.total} label={`그룹 송금 ${prog.total}건 중 ${prog.done}건 완료`} />
            <Label className="num">그룹 송금 {prog.total}건 중 {prog.done}건 완료</Label></>}
        </Tile>

        {driving && (
          <Tile span={2} aria-label="운행 상태">
            <TileHead>
              <div style={{ display: "flex", gap: 12, alignItems: "center", minWidth: 0 }}>
                <IconBox><Icon name="directions_car" /></IconBox>
                <div style={{ minWidth: 0 }}><Label>{driving.name}</Label><Where><Icon name="location_on" />{driving.destination}</Where></div>
              </div>
              <Chip tone="accent"><Icon name="navigation" />운행 중</Chip>
            </TileHead>
          </Tile>
        )}

        <Tile aria-label="참여 그룹">
          <IconBox><Icon name="groups" /></IconBox>
          <div><Label>참여 그룹</Label><Big className="num">{groups.length}<small>개</small></Big></div>
        </Tile>
        <Tile aria-label="진행 중인 정산">
          <IconBox><Icon name="receipt_long" /></IconBox>
          <div><Label>진행 중인 정산</Label><Big className="num">{settling.length}<small>건</small></Big></div>
        </Tile>
      </Bento>

      {owed && <CtaLink href={`/trips/${owed.owed!.tripId}/settle`}>정산하러 가기</CtaLink>}

      <SectionHead>내 그룹</SectionHead>
      <Tile>
        <Rows>
          {groups.map((g) => (
            <li key={g.id}>
              <GroupRow href={`/groups/${g.id}`}>
                <RowText><b>{g.name}</b><Where><Icon name="location_on" />{g.destination}</Where></RowText>
                {g.owed ? <Chip tone="accent">{formatWon(g.owed.amountWon)}</Chip>
                  : g.driving ? <Chip tone="plain">운행 중</Chip> : null}
                <Icon name="chevron_right" />
              </GroupRow>
            </li>
          ))}
        </Rows>
      </Tile>
    </Screen>
  );
}
