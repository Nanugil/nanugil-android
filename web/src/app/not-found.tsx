import { ButtonLink } from "@/ui/Button";
import { Hint, Lead, Section, Title } from "@/ui/kit";
import { Screen } from "@/ui/Screen";

export default function NotFound() {
  return (
    <Screen nav={false}>
      <Section>
        <Title>페이지를 찾을 수 없습니다</Title>
        <Lead>주소가 바뀌었거나 삭제된 페이지입니다.</Lead>
        <Hint>초대 링크로 들어왔다면 운전자에게 새 링크를 요청해 주세요.</Hint>
        <ButtonLink href="/">홈으로</ButtonLink>
      </Section>
    </Screen>
  );
}
