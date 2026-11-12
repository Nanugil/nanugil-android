"use client";
import styled from "@emotion/styled";
import Link from "next/link";
import { formatWon } from "@/lib/format";
import { ButtonLink } from "@/ui/Button";
import { GlassCard } from "@/ui/GlassCard";
import { Icon } from "@/ui/Icon";
import { Screen } from "@/ui/Screen";
import { t } from "@/ui/theme";

export type HomeGroup = {
  id: string; name: string; destination: string; driving: boolean; owed: { tripId: string; amountWon: number } | null;
};

const Title = styled.h1`font-size: ${t.text.x2};`;
const Lead = styled.p`color: ${t.color.ink2};`;
const List = styled.ul`list-style: none; margin: 0; padding: 0; display: grid; gap: ${t.space.md};`;
const CardLink = styled(Link)`text-decoration: none; display: grid; gap: ${t.space.xs}; border-radius: ${t.radius.card};
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }`;
const Name = styled.h2`font-size: ${t.text.lg};`;
const Where = styled.p`display: flex; align-items: center; gap: ${t.space.x2s}; color: ${t.color.ink2}; font-size: ${t.text.sm};
  .material-symbols-rounded { color: ${t.color.warn}; }`;
const Driving = styled.span`display: inline-flex; align-items: center; gap: ${t.space.x2s}; width: fit-content;
  min-height: 28px; padding: 0 ${t.space.sm}; border-radius: ${t.radius.pill};
  background: color-mix(in oklab, var(--color-accent) 22%, transparent); color: ${t.color.ink}; font-size: ${t.text.xs}; font-weight: 600;`;
const Owed = styled.div`display: flex; align-items: center; justify-content: space-between; gap: ${t.space.sm};
  padding-top: ${t.space.sm}; border-top: 1px solid ${t.color.rule};`;
const Amount = styled.p`font-size: ${t.text.xl}; font-weight: 700;`;
const Small = styled.span`display: block; font-size: ${t.text.xs}; color: ${t.color.muted}; font-weight: 400;`;
const SettleLink = styled(Link)`flex: none; display: inline-flex; align-items: center; min-height: 44px; padding: 0 ${t.space.md};
  border-radius: ${t.radius.control}; background: ${t.color.accent}; color: ${t.color.accentInk}; font-weight: 600; text-decoration: none;
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: 2px; }`;

/** 홈. groups가 null이면 불러오기 실패, 빈 배열이면 아직 참여한 그룹이 없다. */
export function HomeView({ groups }: { groups: HomeGroup[] | null }) {
  if (groups === null) {
    return (
      <Screen>
        <GlassCard role="alert">
          <Title>그룹을 불러오지 못했어요</Title>
          <Lead>연결을 확인하고 다시 열어 주세요.</Lead>
          <ButtonLink href="/">다시 불러오기</ButtonLink>
        </GlassCard>
      </Screen>
    );
  }
  if (groups.length === 0) {
    return (
      <Screen>
        <GlassCard>
          <Title>아직 참여한 그룹이 없어요</Title>
          <Lead>운전자 화면의 초대 코드나 QR로 그룹에 들어오면 여기서 정산 요청을 받아요.</Lead>
          <ButtonLink href="/join"><Icon name="qr_code_2" />초대 코드로 참여</ButtonLink>
        </GlassCard>
      </Screen>
    );
  }
  return (
    <Screen>
      <Title>내 그룹</Title>
      <List>
        {groups.map((g) => (
          <li key={g.id}>
            <GlassCard>
              <CardLink href={`/groups/${g.id}`}>
                {g.driving && <Driving><Icon name="directions_car" />운행 중</Driving>}
                <Name>{g.name}</Name>
                <Where><Icon name="location_on" />{g.destination}</Where>
              </CardLink>
              {g.owed && (
                <Owed>
                  <Amount className="num"><Small>보낼 돈</Small>{formatWon(g.owed.amountWon)}</Amount>
                  <SettleLink href={`/trips/${g.owed.tripId}/settle`}>정산하기</SettleLink>
                </Owed>
              )}
            </GlassCard>
          </li>
        ))}
      </List>
    </Screen>
  );
}
