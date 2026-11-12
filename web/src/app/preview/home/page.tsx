import { notFound } from "next/navigation";
import { HomeView } from "../../HomeView";
import { devOnly } from "../mock";

export default function P() {
  if (!devOnly()) notFound();
  return <HomeView groups={[
    { id: "g", name: "2026 신소재공학과 MT", destination: "청평호 펜션", driving: true, owed: { tripId: "t", amountWon: 11750 } },
    { id: "g2", name: "가족 여행", destination: "강릉 바다", driving: false, owed: null },
  ]} />;
}
