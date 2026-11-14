"use client";
import styled from "@emotion/styled";
import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { formatWon } from "@/lib/format";
import { ButtonLink } from "@/ui/Button";
import { DotNumber } from "@/ui/DotNumber";
import { Icon } from "@/ui/Icon";
import { Avatar, Hint, Item, ItemLink, ItemText, Lead, List, Section, SectionHead, Title, Value } from "@/ui/kit";
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

const Top = styled.header`padding-top: 16px;`;
const Amount = styled.div`display: flex; align-items: flex-end; gap: 6px; color: ${t.color.ink};
  > span[aria-hidden] { font-size: 18px; font-weight: 600; line-height: 1; }`;
const Progress = styled.div`display: grid; gap: 8px;`;
const Row = styled.div`display: flex; justify-content: space-between; align-items: baseline; gap: 12px;`;

/** 홈. groups가 null이면 불러오기 실패, 빈 배열이면 참여한 그룹이 없다. */
function OwedAmount({ amountWon }: { amountWon: number }) {
  const shown = useCountUpValue(amountWon);
  return (
    <Amount>
      <span className="visually-hidden">{formatWon(amountWon)}</span>
      <DotNumber text={new Intl.NumberFormat("ko-KR").format(shown)} height={44} /><span aria-hidden="true">원</span>
    </Amount>
  );
}

export function HomeView({ name, groups }: { name: string; groups: HomeGroup[] | null }) {
  const router = useRouter();
  // 개발 전용 미리보기에서는 미리보기 정산 화면으로 보낸다
  const preview = (usePathname() ?? "").startsWith("/preview");
  const settleHref = (tripId: string) => (preview ? "/preview/settle" : `/trips/${tripId}/settle`);
  // 앱으로 돌아오면(다른 탭·앱에서 복귀) 보낼 금액과 진행률을 서버에서 다시 읽는다
  useEffect(() => {
    const onVisible = () => { if (document.visibilityState === "visible" && !preview) router.refresh(); };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [router, preview]);
  const header = <Top><Title>{name ? `${name}님` : "홈"}</Title></Top>;

  if (groups === null || groups.length === 0) {
    const failed = groups === null;
    return (
      <Screen>
        {header}
        <Section>
          <Lead>{failed ? "그룹을 불러오지 못했습니다." : "참여한 그룹이 없습니다."}</Lead>
          <Hint>{failed ? "네트워크 연결을 확인한 뒤 다시 시도해 주세요." : "운전자에게 받은 초대 코드로 참여할 수 있습니다."}</Hint>
          {failed ? <ButtonLink href="/">다시 시도</ButtonLink> : <ButtonLink href="/join">초대 코드 입력</ButtonLink>}
        </Section>
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
            <Row><Hint>그룹 송금</Hint><Hint className="num">{prog.done}/{prog.total} 완료</Hint></Row>
          </Progress>
        )}
        {owed && <SlideToAction label="밀어서 송금하기" actionLabel={`${formatWon(owed.owed!.amountWon)} 송금하러 가기`} onDone={() => router.push(settleHref(owed.owed!.tripId))} />}
      </Section>

      <Section aria-labelledby="groups-title">
        <SectionHead><h2 id="groups-title">내 그룹</h2></SectionHead>
        <List>
          {groups.map((g) => (
            <Item key={g.id}>
              <ItemLink href={`/groups/${g.id}`}>
                <Avatar aria-hidden="true">{g.name.replace(/^[0-9\s]+/, "").slice(0, 1) || g.name.slice(0, 1)}</Avatar>
                <ItemText><strong>{g.name}</strong><span>{g.driving ? `운행 중, ${g.destination}` : g.destination}</span></ItemText>
                {g.owed && <Value>{formatWon(g.owed.amountWon)}</Value>}
                <Icon name="chevron_right" />
              </ItemLink>
            </Item>
          ))}
        </List>
      </Section>
    </Screen>
  );
}
