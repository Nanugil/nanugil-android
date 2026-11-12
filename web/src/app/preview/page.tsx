import { notFound } from "next/navigation";
import { devOnly } from "./mock";
import { PreviewIndex } from "./PreviewIndex";

export default function Preview() {
  if (!devOnly()) notFound();
  return <PreviewIndex />;
}
