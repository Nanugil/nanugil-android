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

const Hello = styled.header`display: flex; align-items: center; gap: ${t.space.sm};`;
const Avatar = styled.span`display: grid; place-items: center; flex: none; width: 40px; height: 40px; border-radius: 12px;
  background: ${t.color.cta}; color: ${t.color.ctaInk}; font-weight: 700;`;
const HelloText = styled.div`display: grid; line-height: 1.2;`;
const Small = styled.span`font-size: ${t.text.xs}; color: ${t.color.muted};`;
const Name = styled.h1`font-size: ${t.text.lg};`;

// 촘촘한 가로 정렬: 왼쪽 라벨, 오른쪽 값. 휴대폰은 2칸, 넓은 화면은 4칸
const Bento = styled.div`display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px;
  @media (min-width: 48rem) { grid-template-columns: repeat(4, minmax(0, 1fr)); }`;
const Tile = styled("section", styleOnly("span", "wideSpan"))<{ span?: number; wideSpan?: number }>`
  grid-column: span ${(p) => p.span ?? 1};
  @media (min-width: 48rem) { grid-column: span ${(p) => p.wideSpan ?? p.span ?? 1}; }
  display: grid; gap: 10px; align-content: start; min-width: 0;
  padding: 14px 16px; border-radius: 18px;
  background: ${t.color.card}; border: 1px solid ${t.color.rule};
`;
const Line = styled.div`display: flex; align-items: center; justify-content: space-between; gap: ${t.space.sm}; min-width: 0;`;
const Left = styled.div`display: flex; align-items: center; gap: 10px; min-width: 0;`;
const Stack = styled.div`display: grid; min-width: 0; line-height: 1.3;
  b { font-size: ${t.text.sm}; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`;
const Label = styled.span`font-size: ${t.text.xs}; color: ${t.color.muted};`;
const IconBox = styled.span`display: grid; place-items: center; flex: none; width: 32px; height: 32px; border-radius: 10px;
  background: ${t.color.cardStrong}; border: 1px solid ${t.color.rule}; color: ${t.color.ink};
  .material-symbols-rounded { font-size: 18px; }`;
const Big = styled.span`font-size: ${t.text.xl}; font-weight: 700; line-height: 1; font-variant-numeric: tabular-nums;
  small { font-size: ${t.text.xs}; font-weight: 500; color: ${t.color.muted}; margin-left: 2px; }`;
const Chip = styled("span", styleOnly("tone"))<{ tone: "accent" | "plain" }>`display: inline-flex; align-items: center; gap: 2px;
  flex: none; min-height: 28px; padding: 0 10px; border-radius: ${t.radius.pill}; white-space: nowrap;
  font-size: ${t.text.xs}; font-weight: 600;
  .material-symbols-rounded { font-size: 16px; }
  border: 1px solid ${(p) => (p.tone === "accent" ? t.color.accent : t.color.rule)};
  color: ${(p) => (p.tone === "accent" ? t.color.accent : t.color.ink2)};
  background: ${(p) => (p.tone === "accent" ? "color-mix(in oklab, var(--color-accent) 8%, var(--color-card))" : t.color.card)};`;
const Amount = styled.div`display: flex; align-items: flex-end; gap: 3px; flex: none; color: ${t.color.ink};
  span { font-size: ${t.text.sm}; font-weight: 700; line-height: 1; }`;
const Gauge = styled.div`display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 10px;
  > div div span { height: 14px; border-radius: 4px; }`;
const Where = styled.span`display: inline-flex; align-items: center; gap: 2px; font-size: ${t.text.xs}; color: ${t.color.ink2};
  .material-symbols-rounded { color: ${t.color.warn}; font-size: 14px; }`;
const SectionHead = styled.h2`font-size: ${t.text.md};`;
const Rows = styled.ul`list-style: none; margin: 0; padding: 0; display: grid;`;
const GroupRow = styled(Link)`display: flex; align-items: center; gap: ${t.space.sm}; min-height: 52px; text-decoration: none;
  border-bottom: 1px solid ${t.color.rule}; li:last-child & { border-bottom: 0; }
  > .material-symbols-rounded { color: ${t.color.muted}; font-size: 20px; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }`;
const Empty = styled.div`display: grid; gap: ${t.space.xs}; justify-items: start;`;
const Body = styled.div`display: grid; gap: 10px;`;

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
            <Stack><b>{failed ? "그룹을 불러오지 못했어요" : "아직 참여한 그룹이 없어요"}</b></Stack>
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
      <Body>
        <Greeting name={name} />
        <Bento>
          <Tile span={2} aria-labelledby="owed">
            <Line>
              <Stack><Label id="owed">{owed ? "보낼 돈" : "정산"}</Label><b>{owed ? owed.name : "보낼 돈이 없어요"}</b></Stack>
              {owed
                ? <Amount aria-label={formatWon(owed.owed!.amountWon)}>
                    <DotNumber text={new Intl.NumberFormat("ko-KR").format(owed.owed!.amountWon)} height={30} /><span aria-hidden="true">원</span>
                  </Amount>
                : <IconBox><Icon name="check" /></IconBox>}
            </Line>
            {prog && (
              <Gauge>
                <div><Segments done={prog.done} total={prog.total} label={`그룹 송금 ${prog.total}건 중 ${prog.done}건 완료`} /></div>
                <Label className="num">{prog.done}/{prog.total} 완료</Label>
              </Gauge>
            )}
          </Tile>

          {driving && (
            <Tile span={2} aria-label="운행 상태">
              <Line>
                <Left>
                  <IconBox><Icon name="directions_car" /></IconBox>
                  <Stack><b>{driving.name}</b><Where><Icon name="location_on" />{driving.destination}</Where></Stack>
                </Left>
                <Chip tone="accent"><Icon name="navigation" />운행 중</Chip>
              </Line>
            </Tile>
          )}

          <Tile aria-label="참여 그룹">
            <Line><Left><IconBox><Icon name="groups" /></IconBox><Label>참여 그룹</Label></Left><Big className="num">{groups.length}<small>개</small></Big></Line>
          </Tile>
          <Tile aria-label="진행 중인 정산">
            <Line><Left><IconBox><Icon name="receipt_long" /></IconBox><Label>진행 정산</Label></Left><Big className="num">{settling.length}<small>건</small></Big></Line>
          </Tile>
        </Bento>

        {owed && <CtaLink href={`/trips/${owed.owed!.tripId}/settle`}>정산하러 가기</CtaLink>}
      </Body>

      <Body>
        <SectionHead>내 그룹</SectionHead>
        <Tile>
          <Rows>
            {groups.map((g) => (
              <li key={g.id}>
                <GroupRow href={`/groups/${g.id}`}>
                  <Stack style={{ flex: 1 }}><b>{g.name}</b><Where><Icon name="location_on" />{g.destination}</Where></Stack>
                  {g.owed ? <Chip tone="accent">{formatWon(g.owed.amountWon)}</Chip>
                    : g.driving ? <Chip tone="plain">운행 중</Chip> : null}
                  <Icon name="chevron_right" />
                </GroupRow>
              </li>
            ))}
          </Rows>
        </Tile>
      </Body>
    </Screen>
  );
}
