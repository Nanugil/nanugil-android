"use client";
import type { TransferStatus } from "@/lib/settlement";
import { Icon } from "./Icon";
import { Tag } from "./kit";

const LOOK: Record<TransferStatus, { label: string; tone: "neutral" | "accent" | "success" }> = {
  pending: { label: "송금 전", tone: "neutral" },
  sent: { label: "확인 대기", tone: "accent" },
  received: { label: "수령 완료", tone: "success" },
};

/** 송금 상태: 색 글자 (완료는 체크 아이콘을 함께 쓴다) */
export function StatusChip({ status }: { status: TransferStatus }) {
  const l = LOOK[status];
  return <Tag tone={l.tone}>{status === "received" && <Icon name="check" />}{l.label}</Tag>;
}
