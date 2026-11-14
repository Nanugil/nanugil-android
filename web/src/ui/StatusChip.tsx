"use client";
import type { TransferStatus } from "@/lib/settlement";
import { Tag } from "./kit";

const LOOK: Record<TransferStatus, { label: string; tone: "neutral" | "accent" | "success" }> = {
  pending: { label: "대기", tone: "neutral" },
  sent: { label: "송금함", tone: "accent" },
  received: { label: "완료", tone: "success" },
};

/** 송금 상태 태그 */
export function StatusChip({ status }: { status: TransferStatus }) {
  const l = LOOK[status];
  return <Tag tone={l.tone}>{l.label}</Tag>;
}
