"use client";
import styled from "@emotion/styled";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { claimMember, joinGroup, myMemberId, previewInvite, unclaimedMembers } from "@/lib/api";
import { ServerError, messageFor } from "@/lib/errors";
import { normalizeInviteCode } from "@/lib/inviteCode";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import type { InviteResult } from "@/lib/types";
import { Button } from "@/ui/Button";
import { CodeInput } from "@/ui/CodeInput";
import { Field } from "@/ui/Field";
import { Avatar, Hint, Item, ItemText, List, PageHeader, Section, Tag } from "@/ui/kit";
import { Icon } from "@/ui/Icon";
import { useShake } from "@/ui/motion";
import { t } from "@/ui/theme";

const ErrorText = styled.p`color: ${t.color.error}; font-size: ${t.text.sm}; min-height: 1lh;`;
// 이름 고르기는 실제 라디오(화살표 키로 이동, 탭 한 번에 들어감). 라벨 전체가 누를 수 있는 줄이다.
const Option = styled.label`display: flex; align-items: center; gap: 12px; min-height: 64px; cursor: pointer; font-size: 15px;
  input { position: absolute; opacity: 0; pointer-events: none; }
  > span:nth-of-type(2) { flex: 1; }
  .mark { color: ${t.color.faint}; font-size: 22px; }
  input:checked ~ .mark { color: ${t.color.accent}; font-variation-settings: "FILL" 1, "wght" 400, "GRAD" 0, "opsz" 24; }
  input:checked ~ span:nth-of-type(2) { font-weight: 600; }
  &:has(input:focus-visible) { outline: 2px solid ${t.color.focus}; outline-offset: 2px; border-radius: 8px; }
`;
const Fieldset = styled.fieldset`border: 0; margin: 0; padding: 0; min-width: 0; display: grid;
  legend { font-size: 15px; font-weight: 600; padding: 0; margin-bottom: 4px; }`;

type Step =
  | { kind: "code" }
  | { kind: "loading" }
  | { kind: "pick"; code: string; group: InviteResult; names: { id: string; displayName: string }[] };

/** 04 코드로 참여: 코드 → 그룹 미리보기 → 이름 고르기(또는 새로 참여) → 그룹 화면 */
export function JoinFlow({ initialCode }: { initialCode: string }) {
  const router = useRouter();
  const sb = createBrowserSupabase();
  const [code, setCode] = useState(initialCode.toUpperCase().slice(0, 6));
  const [step, setStep] = useState<Step>({ kind: "code" });
  const [error, setError] = useState<string | null>(null);
  const [locked, setLocked] = useState(false);
  const [shake, setShake] = useState(0);
  const [picked, setPicked] = useState<string | "new" | null>(null);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const shakeRef = useRef<HTMLDivElement>(null);
  const pickTitle = useRef<HTMLLegendElement>(null);
  useEffect(() => { if (step.kind === "pick") pickTitle.current?.focus(); }, [step.kind]);
  useShake(shakeRef, shake);

  const fail = useCallback((e: unknown) => {
    const c = e instanceof ServerError ? e.code : "UNKNOWN";
    setError(messageFor(c));
    if (c === "INVALID_CODE") setShake((n) => n + 1);
    if (c === "TOO_MANY_ATTEMPTS") setLocked(true);
    setStep({ kind: "code" });
  }, []);

  const lookUp = useCallback(async (raw: string) => {
    const valid = normalizeInviteCode(raw);
    if (!valid) { setError(messageFor("INVALID_CODE")); setShake((n) => n + 1); return; }
    setError(null);
    setStep({ kind: "loading" });
    try {
      const group = await previewInvite(sb, valid);
      // 이미 이 그룹 멤버면 바로 그룹 화면으로
      const already = await myMemberId(sb, group.groupId).then(() => true, () => false);
      if (already) { router.replace(`/groups/${group.groupId}`); return; }
      const names = await unclaimedMembers(sb, valid);
      const { data } = await sb.auth.getUser();
      setName(String(data.user?.user_metadata?.display_name ?? ""));
      setPicked(names.length === 0 ? "new" : null);
      setStep({ kind: "pick", code: valid, group, names });
    } catch (e) { fail(e); }
  }, [sb, router, fail]);

  // QR로 코드가 채워진 채 들어오면 바로 미리보기
  const started = useRef(false);
  useEffect(() => {
    if (started.current || initialCode.length !== 6) return;
    started.current = true;
    void lookUp(initialCode);
  }, [initialCode, lookUp]);

  async function finish() {
    if (step.kind !== "pick" || !picked) return;
    if (picked === "new" && (name.trim().length < 1 || name.trim().length > 20)) { setError("이름은 1~20자로 입력해 주세요."); return; }
    setBusy(true); setError(null);
    try {
      const r = picked === "new" ? await joinGroup(sb, step.code, name.trim()) : await claimMember(sb, step.code, picked);
      router.replace(`/groups/${r.groupId}`);
    } catch (e) {
      const c = e instanceof ServerError ? e.code : "UNKNOWN";
      if (c === "ALREADY_MEMBER") { router.replace(`/groups/${step.group.groupId}`); return; }
      if (c === "MEMBER_TAKEN") {
        setError(messageFor(c));
        setPicked(null);
        setStep({ ...step, names: await unclaimedMembers(sb, step.code).catch(() => []) });
      } else fail(e);
    } finally { setBusy(false); }
  }

  if (step.kind === "pick") {
    return (
      <>
        <PageHeader title="그룹 참여" back="/" />
        <Section aria-labelledby="join-group">
          <List><Item>
            <ItemText><strong id="join-group">{step.group.groupName}</strong><span>참여할 그룹</span></ItemText>
            <Tag className="num">{step.group.memberCount}명</Tag>
          </Item></List>
        </Section>
        <Section>
          <Fieldset>
            <legend ref={pickTitle} tabIndex={-1}>이름 선택</legend>
            {step.names.length > 0 && <Hint>운전자가 등록한 이름 중 본인 이름을 선택해 주세요.</Hint>}
            {step.names.map((m) => (
              <Option key={m.id}>
                <Avatar aria-hidden="true">{m.displayName.slice(0, 1)}</Avatar>
                <span>{m.displayName}</span>
                <input type="radio" name="pick" value={m.id} checked={picked === m.id} onChange={() => setPicked(m.id)} />
                <Icon name={picked === m.id ? "radio_button_checked" : "radio_button_unchecked"} className="mark" />
              </Option>
            ))}
            <Option>
              <Avatar aria-hidden="true"><Icon name="add" /></Avatar>
              <span>새 이름으로 참여</span>
              <input type="radio" name="pick" value="new" checked={picked === "new"} onChange={() => setPicked("new")} />
              <Icon name={picked === "new" ? "radio_button_checked" : "radio_button_unchecked"} className="mark" />
            </Option>
          </Fieldset>
          {picked === "new" && (
            <Field label="이름" value={name} onChange={(e) => setName(e.target.value)} maxLength={20} autoComplete="nickname" help="그룹 멤버에게 표시되는 이름입니다." />
          )}
          {error && <ErrorText role="alert">{error}</ErrorText>}
        </Section>
        <Button type="button" onClick={finish} disabled={!picked || busy} aria-busy={busy}>{busy ? "참여 중" : "참여하기"}</Button>
      </>
    );
  }

  return (
    <>
      <PageHeader title="초대 코드 입력" back="/" />
      <Section aria-labelledby="join-title">
        <Hint id="join-title">운전자 화면에 표시된 6자리 코드를 입력해 주세요.</Hint>
        <CodeInput ref={shakeRef} value={code} onChange={(v) => { setCode(v); setError(null); }}
          onComplete={(v) => void lookUp(v)} disabled={locked} readOnly={step.kind === "loading"} invalid={!!error} errorId={error ? "code-error" : undefined} />
        <ErrorText id="code-error" role={error ? "alert" : undefined}>{error ?? ""}</ErrorText>
      </Section>
      <Button type="button" onClick={() => void lookUp(code)} disabled={locked || code.length !== 6 || step.kind === "loading"}
        aria-busy={step.kind === "loading"}>
        {step.kind === "loading" ? "확인 중" : "확인"}
      </Button>
    </>
  );
}
