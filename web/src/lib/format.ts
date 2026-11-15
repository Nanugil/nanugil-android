const won = new Intl.NumberFormat("ko-KR");

export function formatWon(n: number): string {
  return `${won.format(n)}원`;
}
