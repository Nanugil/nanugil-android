import { notFound } from "next/navigation";
import { Screen } from "@/ui/Screen";
import { JoinFlow } from "../../join/JoinFlow";
import { devOnly } from "../mock";

export default function P() {
  if (!devOnly()) notFound();
  return <Screen><JoinFlow initialCode="" /></Screen>;
}
