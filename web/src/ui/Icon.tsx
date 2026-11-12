/** Material Symbols Rounded 아이콘. 글자 옆 꾸밈이라 스크린 리더에서는 숨긴다. */
export function Icon({ name, className }: { name: string; className?: string }) {
  return <span className={`material-symbols-rounded${className ? ` ${className}` : ""}`} aria-hidden="true">{name}</span>;
}
