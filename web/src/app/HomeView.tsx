"use client";
import styled from "@emotion/styled";
import { usePathname, useRouter } from "next/navigation";
import { formatWon } from "@/lib/format";
import { ButtonLink } from "@/ui/Button";
import { DotNumber } from "@/ui/DotNumber";
import { Icon } from "@/ui/Icon";
import { Hint, Item, ItemLink, ItemText, Lead, List, Section, SectionHead, Stats, Tag, Title } from "@/ui/kit";
import { Screen } from "@/ui/Screen";
import { Segments } from "@/ui/Segments";
import { SlideToAction } from "@/ui/SlideToAction";
import { useCountUpValue } from "@/ui/motion";
import { t } from "@/ui/theme";

export type HomeGroup = {
  id: string; name: string; destination: string; driving: boolean;
  owed: { tripId: string; amountWon: number } | null;
  progress: { done: number; total: number } | null;
};

const Top = styled.header`display: grid; gap: 4px; padding-top: 8px;`;
const Amount = styled.div`display: flex; align-items: flex-end; gap: 6px; color: ${t.color.ink};
  > span { font-size: 18px; font-weight: 600; line-height: 1; }`;
const Progress = styled.div`display: grid; gap: 8px;`;
const Row = styled.div`display: flex; justify-content: space-between; align-items: baseline; gap: 12px;`;

/** 홈. groups가 null이면 불러오기 실패, 빈 배열이면 참여한 그룹이 없다. */
function OwedAmount({ amountWon }: { amountWon: number }) {
  const shown = useCountUpValue(amountWon);
  return (
    <Amount aria-label={formatWon(amountWon)}>
      <DotNumber text={new Intl.NumberFormat("ko-KR").format(shown)} height={44} /><span aria-hidden="true">원</span>
    </Amount>
  );
}

export function HomeView({ name, groups }: { name: string; groups: HomeGroup[] | null }) {
  const router = useRouter();
  // 개발 전용 미리보기에서는 미리보기 정산 화면으로 보낸다
  const preview = (usePathname() ?? "").startsWith("/preview");
  const settleHref = (tripId: string) => (preview ? "/preview/settle" : `/trips/${tripId}/settle`);
  const header = <Top><Hint>나누길</Hint><Title>{name ? `${name}님` : "안녕하세요"}</Title></Top>;

  if (groups === null || groups.length === 0) {
    const failed = groups === null;
    return (
      <Screen>
        {header}
        <Section>
          <Lead>{failed ? "그룹을 불러오지 못했습니다." : "참여한 그룹이 없습니다."}</Lead>
          <Hint>{failed ? "네트워크 연결을 확인한 뒤 다시 시도해 주세요." : "운전자 화면의 초대 코드나 QR로 그룹에 참여할 수 있습니다."}</Hint>
        </Section>
        {failed ? <ButtonLink href="/">다시 시도</ButtonLink> : <ButtonLink href="/join">초대 코드 입력</ButtonLink>}
      </Screen>
    );
  }

  const owed = groups.find((g) => g.owed);
  const settling = groups.filter((g) => g.progress && g.progress.done < g.progress.total);
  const prog = (owed ?? settling[0])?.progress ?? null;

  return (
    <Screen wide>
      {header}

      <Section aria-labelledby="owed-title">
        <SectionHead><h2 id="owed-title">보낼 금액</h2>{owed && <span>{owed.name}</span>}</SectionHead>
        {owed ? (
          <OwedAmount amountWon={owed.owed!.amountWon} />
        ) : <Lead>보낼 금액이 없습니다.</Lead>}
        {prog && (
          <Progress>
            <Segments done={prog.done} total={prog.total} label={`그룹 송금 ${prog.total}건 중 ${prog.done}건 완료`} />
            <Row><Hint>그룹 송금 진행</Hint><Hint className="num">{prog.done}/{prog.total}건 완료</Hint></Row>
          </Progress>
        )}
        {owed && <SlideToAction label="밀어서 정산하기" onDone={() => router.push(settleHref(owed.owed!.tripId))} />}
      </Section>

      <Section aria-label="요약">
        <Stats>
          <div><dt>참여 그룹</dt><dd className="num">{groups.length}<small>개</small></dd></div>
          <div><dt>진행 중인 정산</dt><dd className="num">{settling.length}<small>건</small></dd></div>
        </Stats>
      </Section>

      <Section aria-labelledby="groups-title">
        <SectionHead><h2 id="groups-title">내 그룹</h2></SectionHead>
        <List>
          {groups.map((g) => (
            <Item key={g.id}>
              <ItemLink href={`/groups/${g.id}`}>
                <ItemText><strong>{g.name}</strong><span>{g.driving ? `${g.destination}으로 이동 중` : g.destination}</span></ItemText>
                {g.owed ? <Tag tone="accent">{formatWon(g.owed.amountWon)}</Tag> : g.driving ? <Tag tone="accent">운행 중</Tag> : null}
                <Icon name="chevron_right" />
              </ItemLink>
            </Item>
          ))}
        </List>
      </Section>
    </Screen>
  );
}
