"use client";
import styled from "@emotion/styled";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { safeNext } from "@/lib/next";
import { createBrowserSupabase } from "@/lib/supabase/browser";
import { Button } from "@/ui/Button";
import { Field } from "@/ui/Field";
import { GlassCard } from "@/ui/GlassCard";
import { t } from "@/ui/theme";

const Title = styled.h1`font-size: ${t.text.x2};`;
const Lead = styled.p`color: ${t.color.ink2};`;
const Form = styled.form`display: grid; gap: ${t.space.xs};`;
const Note = styled.p`font-size: ${t.text.xs}; color: ${t.color.muted};`;
const Switch = styled.p`text-align: center; font-size: ${t.text.sm}; color: ${t.color.ink2};
  a { display: inline-block; min-height: 44px; line-height: 44px; color: ${t.color.ink}; font-weight: 600; }`;
const Or = styled.p`display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: ${t.space.sm};
  font-size: ${t.text.xs}; color: ${t.color.muted};
  &::before, &::after { content: ""; height: 1px; background: ${t.color.rule}; }`;
const Social = styled.div`display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: ${t.space.xs};`;
const Logo = styled.img`width: 18px; height: 18px;`;
// 애플 로고는 글자색을 따라가야 라이트 모드에서도 보인다
const AppleLogo = styled.span`width: 18px; height: 18px; background: currentColor;
  mask: url(/apple.svg) center / contain no-repeat; -webkit-mask: url(/apple.svg) center / contain no-repeat;`;
const Status = styled.p`color: ${t.color.error}; font-size: ${t.text.sm};`;

function authMessage(message: string): string {
  if (/invalid login credentials/i.test(message)) return "이메일이나 비밀번호가 맞지 않아요";
  if (/already registered|already been registered/i.test(message)) return "이미 가입한 이메일이에요. 로그인해 주세요";
  if (/rate limit/i.test(message)) return "잠시 후 다시 시도해 주세요";
  if (/password/i.test(message)) return "비밀번호는 8자 이상으로 정해 주세요";
  return "잠시 후 다시 시도해 주세요";
}

/** 로그인·가입 공용 폼. 끝나면 next(같은 사이트 경로만)로 돌아간다. */
export function AuthForm({ mode, next }: { mode: "login" | "signup"; next: string | null }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [socialNote, setSocialNote] = useState(false);
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
      setNameError("이름은 1~20자로 적어 주세요");
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
    if (!res.data.session) { setNotice("가입 확인 메일을 보냈어요. 메일의 링크를 누른 뒤 로그인해 주세요"); return; }
    router.replace(target);
    router.refresh();
  }

  return (
    <GlassCard aria-labelledby="auth-title">
      <Title id="auth-title">{signup ? "나누길 가입" : "나누길 로그인"}</Title>
      <Lead>{signup ? "정산 요청을 받으려면 가입해 주세요." : "일행이 보낸 정산 요청을 확인해요."}</Lead>
      <Form onSubmit={onSubmit} noValidate={false}>
        <Field label="이메일" icon="mail" name="email" type="email" autoComplete="email" inputMode="email" required />
        <Field label="비밀번호" icon="lock" name="password" type="password" minLength={8} required
          autoComplete={signup ? "new-password" : "current-password"} help={signup ? "8자 이상" : undefined} />
        {signup && (
          <Field label="이름" icon="person" name="displayName" autoComplete="nickname" maxLength={20} required
            help="일행에게 보이는 이름이에요" error={nameError} />
        )}
        {signup && <Note>이메일과 이름만 받아요. 이메일은 다른 멤버에게 보이지 않아요.</Note>}
        {error && <Status role="alert">{error}</Status>}
        {notice && <Lead role="status">{notice}</Lead>}
        <Button type="submit" disabled={busy} aria-busy={busy}>{signup ? "가입하기" : "로그인"}</Button>
      </Form>
      <Or>또는</Or>
      {/* MVP: 소셜 로그인은 아직 연결 전이라 누르면 안내만 한다 */}
      <Social>
        <Button type="button" variant="ghost" onClick={() => setSocialNote(true)}><AppleLogo aria-hidden="true" />Apple</Button>
        <Button type="button" variant="ghost" onClick={() => setSocialNote(true)}><Logo src="/google.svg" alt="" width={18} height={18} />Google</Button>
      </Social>
      {socialNote && <Lead role="status">소셜 로그인은 곧 지원해요. 지금은 이메일로 이용해 주세요.</Lead>}
      <Switch>
        {signup ? "이미 가입했나요? " : "처음이에요? "}
        <Link href={other}>{signup ? "로그인" : "가입하기"}</Link>
      </Switch>
    </GlassCard>
  );
}
