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
import { GlassCard } from "@/ui/GlassCard";
import { Icon } from "@/ui/Icon";
import { useShake } from "@/ui/motion";
import { styleOnly } from "@/ui/styleProps";
import { t } from "@/ui/theme";

const Title = styled.h1`font-size: ${t.text.x2};`;
const Lead = styled.p`color: ${t.color.ink2};`;
const ErrorText = styled.p`color: ${t.color.error}; font-size: ${t.text.sm}; min-height: 1lh;`;
const GroupName = styled.h2`font-size: ${t.text.xl};`;
const Count = styled.p`color: ${t.color.muted}; font-size: ${t.text.sm};`;
const List = styled.ul`list-style: none; margin: 0; padding: 0; display: grid;`;
const Pick = styled("button", styleOnly("selected"))<{ selected: boolean }>`
  display: flex; align-items: center; justify-content: space-between; gap: ${t.space.sm};
  width: 100%; min-height: 52px; padding: 0 ${t.space.xs};
  background: transparent; border: 0; border-bottom: 1px solid ${t.color.rule};
  text-align: left; font-size: ${t.text.md}; cursor: pointer;
  color: ${(p) => (p.selected ? t.color.ink : t.color.ink2)};
  font-weight: ${(p) => (p.selected ? 600 : 400)};
  .material-symbols-rounded { color: ${(p) => (p.selected ? t.color.accent : t.color.muted)}; }
  &:focus-visible { outline: 2px solid ${t.color.focus}; outline-offset: -2px; }
`;

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
    if (picked === "new" && (name.trim().length < 1 || name.trim().length > 20)) { setError("이름은 1~20자로 적어 주세요"); return; }
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
      <GlassCard aria-labelledby="join-group">
        <GroupName id="join-group">{step.group.groupName}</GroupName>
        <Count className="num">지금 {step.group.memberCount}명이 함께해요</Count>
        {step.names.length > 0 && (
          <>
            <Lead>운전자가 미리 넣어 둔 이름 중에 내 이름이 있으면 골라 주세요.</Lead>
            <List role="radiogroup" aria-label="내 이름 고르기">
              {step.names.map((m) => (
                <li key={m.id}>
                  <Pick type="button" role="radio" aria-checked={picked === m.id} selected={picked === m.id} onClick={() => setPicked(m.id)}>
                    {m.displayName}<Icon name={picked === m.id ? "radio_button_checked" : "radio_button_unchecked"} />
                  </Pick>
                </li>
              ))}
              <li>
                <Pick type="button" role="radio" aria-checked={picked === "new"} selected={picked === "new"} onClick={() => setPicked("new")}>
                  목록에 없어요, 새로 참여할게요<Icon name={picked === "new" ? "radio_button_checked" : "radio_button_unchecked"} />
                </Pick>
              </li>
            </List>
          </>
        )}
        {picked === "new" && (
          <Field label="이름" value={name} onChange={(e) => setName(e.target.value)} maxLength={20} autoComplete="nickname" help="일행에게 보이는 이름이에요" />
        )}
        <ErrorText role={error ? "alert" : undefined}>{error ?? ""}</ErrorText>
        <Button type="button" onClick={finish} disabled={!picked || busy} aria-busy={busy}>참여하기</Button>
      </GlassCard>
    );
  }

  return (
    <GlassCard aria-labelledby="join-title">
      <Title id="join-title">코드로 참여</Title>
      <Lead>운전자 화면에 보이는 초대 코드를 넣어 주세요.</Lead>
      <CodeInput ref={shakeRef} value={code} onChange={(v) => { setCode(v); setError(null); }}
        onComplete={(v) => void lookUp(v)} disabled={locked || step.kind === "loading"} invalid={!!error} />
      <ErrorText role={error ? "alert" : undefined}>{error ?? ""}</ErrorText>
      <Button type="button" onClick={() => void lookUp(code)} disabled={locked || code.length !== 6 || step.kind === "loading"}
        aria-busy={step.kind === "loading"}>
        {step.kind === "loading" ? "찾는 중" : "그룹 찾기"}
      </Button>
    </GlassCard>
  );
}
