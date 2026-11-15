import { Screen } from "@/ui/Screen";
import { JoinFlow } from "./JoinFlow";

export default async function JoinPage({ searchParams }: { searchParams: Promise<{ code?: string }> }) {
  const { code } = await searchParams;
  return <Screen><JoinFlow initialCode={code ?? ""} /></Screen>;
}
