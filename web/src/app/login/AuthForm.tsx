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
const Brand = styled.div`display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 700; color: ${t.color.ink2};`;
const Intro = styled.div`display: grid; gap: 8px;`;
const Mark = styled.img`width: 28px; height: 28px; border-radius: 8px;`;
const Title = styled.h1`font-size: 28px; font-weight: 700; line-height: 1.32; letter-spacing: -0.02em;`;
const Form = styled.form`display: grid; gap: 20px; > button { margin-top: 4px; }`;
const Or = styled.p`display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: ${t.space.sm};
  font-size: ${t.text.xs}; color: ${t.color.muted};
  &::before, &::after { content: ""; height: 1px; background: ${t.color.rule}; }`;
const Social = styled.div`display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; > button { font-weight: 500; }`;
const Logo = styled.img`width: 18px; height: 18px;`;
const AppleLogo = styled.span`width: 18px; height: 18px; background: currentColor;
  mask: url(/apple.svg) center / contain no-repeat; -webkit-mask: url(/apple.svg) center / contain no-repeat;`;
const Switch = styled.p`text-align: center; font-size: ${t.text.sm}; color: ${t.color.ink2};
  a { display: inline-block; min-height: 44px; line-height: 44px; padding: 0 4px; color: ${t.color.ink}; font-weight: 600; }`;
const Status = styled.p<{ error?: boolean }>`font-size: 13px; min-height: 1lh; text-align: center;
  color: ${(p) => (p.error ? t.color.error : t.color.ink2)};`;

function authMessage(message: string): string {
  if (/invalid login credentials/i.test(message)) return "이메일 또는 비밀번호가 올바르지 않습니다.";
  if (/already registered|already been registered/i.test(message)) return "이미 가입된 이메일입니다. 로그인해 주세요.";
  if (/rate limit/i.test(message)) return "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.";
  if (/password/i.test(message)) return "비밀번호를 8자 이상 입력해 주세요.";
  return "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
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
      setNameError("이름은 1~20자로 입력해 주세요.");
      (e.currentTarget.elements.namedItem("displayName") as HTMLInputElement).focus();
      return;
    }
    setBusy(true);
    const sb = createBrowserSupabase();
    let res;
    try {
      res = signup
        ? await sb.auth.signUp({ email, password, options: { data: { display_name: name } } })
        : await sb.auth.signInWithPassword({ email, password });
    } catch {
      setError("네트워크 연결을 확인한 뒤 다시 시도해 주세요.");
      return;
    } finally { setBusy(false); }
    if (res.error) { setError(authMessage(res.error.message)); return; }
    if (!res.data.session) { setNotice("가입 확인 메일을 보냈습니다. 메일의 링크를 누른 뒤 로그인해 주세요."); return; }
    router.replace(target);
    router.refresh();
  }

  return (
    <Wrap>
      <Brand><Mark src="/icon.svg" alt="" width={28} height={28} />나누길</Brand>
      <Intro>
        <Title>{signup ? <>이메일로<br />회원가입</> : <>함께 탄 길,<br />정산은 나누길</>}</Title>
        <Text>{signup ? "가입하면 그룹의 정산 요청을 받을 수 있습니다." : "그룹의 정산 요청을 확인하려면 로그인해 주세요."}</Text>
      </Intro>
      <Form onSubmit={onSubmit}>
        <Field label="이메일" name="email" type="email" autoComplete="email" inputMode="email" required />
        <Field label="비밀번호" name="password" type="password" minLength={8} required
          autoComplete={signup ? "new-password" : "current-password"} help={signup ? "8자 이상 입력" : undefined} />
        {signup && <Field label="이름" name="displayName" autoComplete="nickname" maxLength={20} required
          help="그룹 멤버에게 표시되는 이름입니다. 이메일은 공개되지 않습니다." error={nameError} />}
                {error && <Status role="alert" error>{error}</Status>}
        <Button type="submit" disabled={busy} aria-busy={busy}>{busy ? "처리 중" : signup ? "가입하기" : "로그인"}</Button>
      </Form>
      <Or>또는</Or>
      {/* MVP: 소셜 로그인은 아직 연결 전이라 누르면 안내만 한다 */}
      <Social>
        <Button type="button" variant="ghost" onClick={() => setNotice("소셜 로그인은 준비 중입니다. 이메일로 로그인해 주세요.")}><AppleLogo aria-hidden="true" />Apple</Button>
        <Button type="button" variant="ghost" onClick={() => setNotice("소셜 로그인은 준비 중입니다. 이메일로 로그인해 주세요.")}><Logo src="/google.svg" alt="" width={18} height={18} />Google</Button>
      </Social>
      <Status role="status" aria-live="polite">{notice ?? ""}</Status>
      <Switch><Link href={other}>{signup ? "로그인으로 돌아가기" : "이메일로 회원가입"}</Link></Switch>
    </Wrap>
  );
}
