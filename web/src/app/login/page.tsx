import { Screen } from "@/ui/Screen";
import { AuthForm } from "./AuthForm";

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const { next } = await searchParams;
  return <Screen nav={false}><AuthForm mode="login" next={next ?? null} /></Screen>;
}
