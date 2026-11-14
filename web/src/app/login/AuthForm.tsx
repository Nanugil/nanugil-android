"use client";
import styled from "@emotion/styled";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { safeNext } from "@/lib/next";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { Lead as Text } from "@/ui/kit";
import { t } from "@/ui/theme";

const Wrap = styled.div`display: grid; gap: 28px; padding-top: 32px;`;
const Brand = styled.div`display: flex; align-items: center; gap: 10px; font-size: 17px; font-weight: 700; letter-spacing: -0.01em;`;
const Intro = styled.div`display: grid; gap: 8px;`;
const Mark = styled.img`width: 32px; height: 32px; border-radius: 9px;`;
const Title = styled.h1`font-size: 26px; font-weight: 700; line-height: 1.3; letter-spacing: -0.02em;`;
const Form = styled.form`display: grid; gap: 4px; > button { margin-top: 8px; }`;
const Or = styled.p`display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: ${t.space.sm};
  font-size: ${t.text.xs}; color: ${t.color.muted};
  &::before, &::after { content: ""; height: 1px; background: ${t.color.rule}; }`;
const Social = styled.div`display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px;
  > button { background: ${t.color.card}; border: 1px solid var(--color-input-line); font-weight: 500; }`;
const Logo = styled.img`width: 18px; height: 18px;`;
const AppleLogo = styled.span`width: 18px; height: 18px; background: currentColor;
  mask: url(/apple.svg) center / contain no-repeat; -webkit-mask: url(/apple.svg) center / contain no-repeat;`;
const Switch = styled.p`text-align: center; font-size: ${t.text.sm}; color: ${t.color.ink2};
  a { display: inline-block; min-height: 44px; line-height: 44px; padding: 0 4px; color: ${t.color.ink}; font-weight: 600; }`;
const Status = styled.p<{ error?: boolean }>`font-size: ${t.text.sm}; padding-bottom: ${t.space.xs};
  color: ${(p) => (p.error ? t.color.error : t.color.ink2)};`;
const Note = styled.p`font-size: ${t.text.xs}; color: ${t.color.muted}; padding-bottom: ${t.space.sm};`;

function authMessage(message: string): string {
  if (/invalid login credentials/i.test(message)) return "이메일 또는 비밀번호가 올바르지 않습니다";
  if (/already registered|already been registered/i.test(message)) return "이미 가입된 이메일입니다. 로그인해 주세요";
  if (/rate limit/i.test(message)) return "요청이 많습니다. 잠시 후 다시 시도해 주세요";
  if (/password/i.test(message)) return "비밀번호는 8자 이상이어야 합니다";
  return "일시적인 오류입니다. 잠시 후 다시 시도해 주세요";
}

/** 로그인·가입 공용 화면. 끝나면 next(같은 사이트 경로만)로 돌아간다. */
export function AuthForm({ mode, next }: { mode: "login" | "signup"; next: string | null }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const signup = mode === "signup";
  const target = safeNext(next);
  const other = `${signup ? "/login" : "/signup"}${next ? `?next=${encodeURIComponent(target)}` : ""}`;

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const email = String(f.get("email")).trim();
    const password = String(f.get("password"));
    const name = String(f.get("displayName") ?? "").trim();
    setError(null); setNameError(null); setNotice(null);
    if (signup && (name.length < 1 || name.length > 20)) {
      setNameError("이름은 1~20자로 입력해 주세요");
      (e.currentTarget.elements.namedItem("displayName") as HTMLInputElement).focus();
      return;
    }
    setBusy(true);
    const sb = createBrowserSupabase();
    const res = signup
      ? await sb.auth.signUp({ email, password, options: { data: { display_name: name } } })
      : await sb.auth.signInWithPassword({ email, password });
    setBusy(false);
    if (res.error) { setError(authMessage(res.error.message)); return; }
    if (!res.data.session) { setNotice("가입 확인 메일을 보냈습니다. 메일의 링크를 누른 뒤 로그인해 주세요."); return; }
    router.replace(target);
    router.refresh();
  }

  return (
    <Wrap>
      <Brand><Mark src="/icon.svg" alt="" width={32} height={32} />나누길</Brand>
      <Intro>
        <Title>{signup ? "회원가입" : "로그인"}</Title>
        <Text>{signup ? "이메일로 가입하고 그룹의 정산 요청을 받아 보세요." : "그룹의 정산 요청을 확인하려면 로그인해 주세요."}</Text>
      </Intro>
      <Form onSubmit={onSubmit}>
        <Field label="이메일" icon="mail" name="email" type="email" autoComplete="email" inputMode="email" required />
        <Field label="비밀번호" icon="lock" name="password" type="password" minLength={8} required
          autoComplete={signup ? "new-password" : "current-password"} help={signup ? "8자 이상" : undefined} />
        {signup && <Field label="이름" icon="person" name="displayName" autoComplete="nickname" maxLength={20} required
          help="그룹 멤버에게 표시되는 이름입니다." error={nameError} />}
        {signup && <Note>이메일은 로그인에만 사용하며 다른 멤버에게 공개되지 않습니다.</Note>}
        {error && <Status role="alert" error>{error}</Status>}
        {notice && <Status role="status">{notice}</Status>}
        <Button type="submit" disabled={busy} aria-busy={busy}>{busy ? "처리 중" : signup ? "가입하기" : "로그인"}</Button>
      </Form>
      <Or>또는</Or>
      {/* MVP: 소셜 로그인은 아직 연결 전이라 누르면 안내만 한다 */}
      <Social>
        <Button type="button" variant="ghost" onClick={() => setNotice("소셜 로그인은 준비 중입니다. 이메일로 이용해 주세요.")}><AppleLogo aria-hidden="true" />Apple</Button>
        <Button type="button" variant="ghost" onClick={() => setNotice("소셜 로그인은 준비 중입니다. 이메일로 이용해 주세요.")}><Logo src="/google.svg" alt="" width={18} height={18} />Google</Button>
      </Social>
      <Switch>{signup ? "이미 계정이 있으신가요?" : "계정이 없으신가요?"} <Link href={other}>{signup ? "로그인" : "회원가입"}</Link></Switch>
    </Wrap>
  );
}
