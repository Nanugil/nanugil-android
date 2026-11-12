import { notFound } from "next/navigation";
import { GroupLive } from "../../groups/[id]/GroupLive";
import { devOnly, group, members, trip } from "../mock";

export default function P() {
  if (!devOnly()) notFound();
  return <GroupLive state={{ kind: "ok", group, members, trips: [trip(null)], me: "p1" }} />;
}
