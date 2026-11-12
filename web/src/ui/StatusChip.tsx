"use client";
import styled from "@emotion/styled";
import type { TransferStatus } from "@/lib/settlement";
import { styleOnly } from "./styleProps";
import { t } from "./theme";

const LABEL: Record<TransferStatus, string> = { pending: "확인 전", sent: "보냈어요", received: "송금 완료" };
const COLOR: Record<TransferStatus, string> = { pending: t.color.muted, sent: t.color.accent, received: t.color.success };

const Chip = styled("span", styleOnly("status"))<{ status: TransferStatus }>`
  display: inline-flex; align-items: center; min-height: 28px; padding: 0 ${t.space.sm};
  border-radius: ${t.radius.pill}; font-size: ${t.text.xs}; font-weight: 600; white-space: nowrap;
  color: ${(p) => (p.status === "pending" ? t.color.ink2 : t.color.accentInk)};
  background: ${(p) => (p.status === "pending" ? t.color.cardStrong : COLOR[p.status])};
  transition: opacity ${t.dur.short} ${t.ease.out};
`;

/** 09 화면의 송금 상태 칩 */
export function StatusChip({ status }: { status: TransferStatus }) {
  return <Chip status={status}>{LABEL[status]}</Chip>;
}
