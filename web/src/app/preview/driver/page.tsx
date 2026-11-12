import { notFound } from "next/navigation";
import { SettleView } from "../../trips/[id]/settle/SettleView";
import { devOnly, members, settlement, trip } from "../mock";

export default function P() {
  if (!devOnly()) notFound();
  return <SettleView state={{ kind: "ok", trip: trip(settlement, true), members, me: "a" }} />;
}
