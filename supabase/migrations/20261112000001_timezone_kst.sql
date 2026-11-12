-- DB 기본 시간대를 한국 시간(KST)으로 바꾼다. 대시보드와 API 응답의 시각이 +09로 보인다.
-- 저장 값(timestamptz)은 그대로이고 보이는 방식만 바뀐다. 24시간 만료 같은 계산에도 영향 없다.
alter database postgres set timezone to 'Asia/Seoul';
