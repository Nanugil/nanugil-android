"use client";
import type { TransferStatus } from "@/lib/settlement";
import { Icon } from "./Icon";
import { Chip } from "./kit";

const LOOK: Record<TransferStatus, { label: string; tone: "plain" | "accent" | "success"; icon?: string }> = {
  pending: { label: "확인 전", tone: "plain" },
  sent: { label: "보냈어요", tone: "accent", icon: "send" },
  received: { label: "송금 완료", tone: "success", icon: "check" },
};

/** 송금 상태 칩. 색만으로 구분하지 않도록 아이콘과 글자를 함께 쓴다. */
export function StatusChip({ status }: { status: TransferStatus }) {
  const l = LOOK[status];
  return <Chip tone={l.tone}>{l.icon && <Icon name={l.icon} />}{l.label}</Chip>;
}
