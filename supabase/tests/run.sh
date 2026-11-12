#!/bin/sh
# 임시 Postgres를 띄워 스텁 + 마이그레이션을 적용하고 SQL 테스트를 돌린다. 실제 Supabase에는 붙지 않는다.
# 사용: supabase/tests/run.sh [테스트 파일...]   (기본: supabase/tests/*_test.sql)
# SKIP_MIGRATION=<파일 이름 일부> 를 주면 그 마이그레이션은 빼고 적용한다.
set -eu
cd "$(dirname "$0")/.."
DIR=$(mktemp -d)
PORT=${PGTEST_PORT:-55432}
trap 'pg_ctl -D "$DIR/data" -m immediate stop >/dev/null 2>&1 || true; rm -rf "$DIR"' EXIT
initdb -D "$DIR/data" -U postgres -A trust --locale=C -E UTF8 >/dev/null
pg_ctl -D "$DIR/data" -o "-p $PORT -k $DIR -c listen_addresses=''" -l "$DIR/log" -w start >/dev/null
PSQL="psql -X -h $DIR -p $PORT -U postgres -d postgres -v ON_ERROR_STOP=1 -q"
$PSQL -f tests/supabase_stub.sql
for f in migrations/*.sql; do
  if [ -n "${SKIP_MIGRATION:-}" ] && echo "$f" | grep -q "$SKIP_MIGRATION"; then continue; fi
  $PSQL -f "$f"
done
if [ $# -eq 0 ]; then set -- tests/*_test.sql; fi
for t in "$@"; do echo "== $t"; $PSQL -f "$t"; done
echo "ALL PASSED"
