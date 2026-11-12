import { Screen } from "@/ui/Screen";
import { AuthForm } from "../login/AuthForm";

export default async function SignupPage({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const { next } = await searchParams;
  return <Screen><AuthForm mode="signup" next={next ?? null} /></Screen>;
}
