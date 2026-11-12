import { notFound } from "next/navigation";
import { HomeView } from "../../HomeView";
import { devOnly } from "../mock";

export default function P() {
  if (!devOnly()) notFound();
  return <HomeView groups={[]} />;
}
