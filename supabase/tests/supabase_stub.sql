-- 로컬 Postgres에서 마이그레이션을 시험하려고 Supabase가 미리 만들어 두는 것만 흉내 낸다.
-- auth.uid()는 세션 설정 request.jwt.claim.sub를 읽는다 (set request.jwt.claim.sub = '<uuid>').
do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated nologin; end if;
end $$;
grant usage on schema public to anon, authenticated;

create schema auth;
grant usage on schema auth to authenticated;
create table auth.users (id uuid primary key default gen_random_uuid(), raw_app_meta_data jsonb default '{}'::jsonb, is_anonymous boolean not null default false);
create function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;

create schema realtime;
create table realtime.messages (id bigserial primary key, topic text, extension text);
alter table realtime.messages enable row level security;
create function realtime.topic() returns text language sql stable as $$ select current_setting('realtime.topic', true) $$;
create publication supabase_realtime;
