import { formatWon } from "./format";

/**
 * 권한을 허락한 경우에만 브라우저 알림을 띄운다. 화면이 열려 있을 때만 동작한다 (Web Push는 2단계).
 * Android Chrome은 페이지에서 new Notification()을 막아 TypeError를 던지므로, 그때는 서비스 워커로 띄우고
 * 그것도 없으면 조용히 넘어간다. 알림은 부가 기능이라 실패해도 화면 이동을 막지 않는다.
 */
export function notifySettlement(groupName: string, amountWon: number): void {
  if (typeof Notification === "undefined" || Notification.permission !== "granted") return;
  const title = "정산 요청";
  const options = { body: `${groupName}에서 ${formatWon(amountWon)} 송금을 요청했습니다.`, icon: "/icon.svg" };
  try {
    new Notification(title, options);
  } catch {
    navigator.serviceWorker?.getRegistration()
      .then((reg) => reg?.showNotification(title, options))
      .catch(() => {});
  }
}

/** 버튼을 눌렀을 때만 부른다 (자동으로 권한을 묻지 않는다). */
export async function askNotificationPermission(): Promise<NotificationPermission | "unsupported"> {
  if (typeof Notification === "undefined") return "unsupported";
  return Notification.requestPermission();
}
