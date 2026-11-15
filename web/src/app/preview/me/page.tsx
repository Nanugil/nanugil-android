import { notFound } from "next/navigation";
import { MeView } from "../../me/MeView";
import { devOnly } from "../mock";

export default function P() {
  if (!devOnly()) notFound();
  return <MeView name="김민수" email="minsu@example.com" />;
}
