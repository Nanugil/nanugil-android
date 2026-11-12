/** 서버 group_members.pay_link 검사, 차량 앱 normalizeKakaoPayLink와 같은 규칙 */
const KAKAOPAY = /^https:\/\/([a-z0-9-]+\.)*kakaopay\.com(\/.*)?$/;

export function isKakaoPayLink(url: string | null): boolean {
  return url != null && KAKAOPAY.test(url);
}
