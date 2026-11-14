import { formatWon } from "./format";

/** 권한을 허락한 경우에만 브라우저 알림을 띄운다. 화면이 열려 있을 때만 동작한다 (Web Push는 2단계). */
export function notifySettlement(groupName: string, amountWon: number): void {
  if (typeof Notification === "undefined" || Notification.permission !== "granted") return;
  new Notification("정산 요청", { body: `${groupName}에서 ${formatWon(amountWon)} 송금을 요청했습니다`, icon: "/icon.svg" });
}

/** 버튼을 눌렀을 때만 부른다 (자동으로 권한을 묻지 않는다). */
export async function askNotificationPermission(): Promise<NotificationPermission | "unsupported"> {
  if (typeof Notification === "undefined") return "unsupported";
  return Notification.requestPermission();
}
