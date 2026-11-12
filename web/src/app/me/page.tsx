import { createServerSupabase } from "@/lib/supabase/server";
import { MeView } from "./MeView";

export default async function MePage() {
  const { data } = await (await createServerSupabase()).auth.getUser();
  return <MeView name={String(data.user?.user_metadata?.display_name ?? "")} email={data.user?.email ?? ""} />;
}
