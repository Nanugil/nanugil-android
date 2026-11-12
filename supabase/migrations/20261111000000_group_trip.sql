-- 그룹·초대·운행 서버 (docs/group-trip-api.md, ADR-001)
-- 테이블 4개 + RLS(읽기만) + RPC(쓰기 전부) + Realtime private 채널 정책.
-- 오류는 `raise exception '오류코드'`로 돌려준다. 단, join_group·preview_invite는 실패 횟수를
-- 남겨야 해서(예외를 던지면 기록이 되돌려진다) 결과의 error 열로 돌려준다.

-- ─────────────────────────────────────────────
-- 테이블
-- ─────────────────────────────────────────────

create table public.groups (
  id                     uuid primary key default gen_random_uuid(),
  name                   text not null check (length(btrim(name)) between 1 and 40),
  logo_url               text,
  destination_name       text not null check (length(btrim(destination_name)) >= 1),
  destination_lat        double precision not null check (destination_lat between -90 and 90),
  destination_lng        double precision not null check (destination_lng between -180 and 180),
  owner_id               uuid not null references auth.users (id) on delete cascade,
  invite_code            text not null unique check (invite_code ~ '^[A-HJ-NP-Z2-9]{6}$'),
  invite_code_expires_at timestamptz not null,
  created_at             timestamptz not null default now()
);

create table public.group_members (
  id           uuid primary key default gen_random_uuid(),
  group_id     uuid not null references public.groups (id) on delete cascade,
  user_id      uuid references auth.users (id) on delete cascade, -- 앱 없는 동승자(I4)는 null
  display_name text not null check (length(btrim(display_name)) between 1 and 20),
  joined_at    timestamptz not null default now(),
  unique (group_id, user_id)
);

create table public.trips (
  id               uuid primary key default gen_random_uuid(),
  group_id         uuid not null references public.groups (id) on delete cascade,
  started_by       uuid references auth.users (id) on delete set null,
  started_at       timestamptz not null default now(),
  ended_at         timestamptz, -- null이면 진행 중
  destination_name text not null,
  destination_lat  double precision not null,
  destination_lng  double precision not null,
  check (ended_at is null or ended_at >= started_at)
);

-- 그룹당 진행 중 운행은 하나
create unique index trips_one_active_per_group on public.trips (group_id) where ended_at is null;

create table public.trip_members (
  trip_id                  uuid not null references public.trips (id) on delete cascade,
  member_id                uuid not null references public.group_members (id) on delete cascade,
  vehicle_driver_member_id uuid not null, -- 운전자 행은 자기 member_id
  joined_at                timestamptz not null default now(),
  -- 아래는 운전자 행만
  vehicle_label            text check (length(btrim(vehicle_label)) between 1 and 20),
  fuel_type                text check (fuel_type in ('GASOLINE', 'PREMIUM_GASOLINE', 'DIESEL', 'LPG', 'EV', 'HYBRID', 'HYDROGEN')),
  arrived_at               timestamptz,
  start_fuel_ml            bigint  check (start_fuel_ml >= 0),
  refueled_ml              bigint  check (refueled_ml >= 0),
  end_fuel_ml              bigint  check (end_fuel_ml >= 0),
  fuel_calc_method         text    check (fuel_calc_method in ('FUEL_LEVEL', 'PERCENT_CAPACITY', 'DISTANCE_EFFICIENCY')),
  price_basis              text    check (price_basis in ('NEAREST', 'CHEAPEST')),
  nearest_price            integer check (nearest_price >= 0),
  cheapest_price           integer check (cheapest_price >= 0),
  applied_price            integer check (applied_price >= 0),
  start_battery_wh         bigint  check (start_battery_wh >= 0),
  charged_wh               bigint  check (charged_wh >= 0),
  end_battery_wh           bigint  check (end_battery_wh >= 0),
  applied_price_per_kwh    integer check (applied_price_per_kwh >= 0),
  toll_estimated           integer check (toll_estimated >= 0),
  toll_adjusted            integer check (toll_adjusted >= 0),
  calculated_at            timestamptz,
  primary key (trip_id, member_id),
  -- 탄 차는 같은 운행의 운전자 행이어야 한다. 운전자 행이 지워지면 그 차 동승자도 지워진다.
  foreign key (trip_id, vehicle_driver_member_id) references public.trip_members (trip_id, member_id) on delete cascade,
  -- 운전자 행은 차량 정보가 있고, 동승자 행은 차량·비용 열이 비어 있다.
  check (
    (member_id = vehicle_driver_member_id and vehicle_label is not null and fuel_type is not null)
    or (member_id <> vehicle_driver_member_id
        and num_nonnulls(vehicle_label, fuel_type, arrived_at, start_fuel_ml, refueled_ml, end_fuel_ml,
                         fuel_calc_method, price_basis, nearest_price, cheapest_price, applied_price,
                         start_battery_wh, charged_wh, end_battery_wh, applied_price_per_kwh,
                         toll_estimated, toll_adjusted, calculated_at) = 0)
  )
);

create index trip_members_driver on public.trip_members (trip_id, vehicle_driver_member_id);
create index group_members_user on public.group_members (user_id);

-- ─────────────────────────────────────────────
-- 읽기 권한 (RLS). 쓰기 정책은 만들지 않는다 → 테이블 직접 쓰기는 거부.
-- ─────────────────────────────────────────────

create function public.is_group_member(p_group_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.group_members
    where group_id = p_group_id and user_id = auth.uid()
  );
$$;

create function public.is_trip_group_member(p_trip_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.trips t
    join public.group_members m on m.group_id = t.group_id
    where t.id = p_trip_id and m.user_id = auth.uid()
  );
$$;

alter table public.groups        enable row level security;
alter table public.group_members enable row level security;
alter table public.trips         enable row level security;
alter table public.trip_members  enable row level security;

create policy "멤버만 읽기" on public.groups        for select to authenticated using (public.is_group_member(id));
create policy "멤버만 읽기" on public.group_members for select to authenticated using (public.is_group_member(group_id));
create policy "멤버만 읽기" on public.trips         for select to authenticated using (public.is_group_member(group_id));
create policy "멤버만 읽기" on public.trip_members  for select to authenticated using (public.is_trip_group_member(trip_id));

revoke all on public.groups, public.group_members, public.trips, public.trip_members from anon, authenticated;
grant select on public.groups, public.group_members, public.trips, public.trip_members to authenticated;

-- ─────────────────────────────────────────────
-- 내부 도우미
-- ─────────────────────────────────────────────

-- 호출한 사람의 그룹 멤버 ID. 멤버가 아니면 NOT_MEMBER.
create function public._my_member_id(p_group_id uuid)
returns uuid language plpgsql stable security definer set search_path = '' as $$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_MEMBER'; end if;
  select id into v_id from public.group_members where group_id = p_group_id and user_id = auth.uid();
  if v_id is null then raise exception 'NOT_MEMBER'; end if;
  return v_id;
end;
$$;

create function public._require_owner(p_group_id uuid)
returns void language plpgsql stable security definer set search_path = '' as $$
begin
  perform public._my_member_id(p_group_id);
  if not exists (select 1 from public.groups where id = p_group_id and owner_id = auth.uid()) then
    raise exception 'NOT_OWNER';
  end if;
end;
$$;

-- 진행 중 운행의 그룹 ID. 운행이 없거나 끝났으면 TRIP_NOT_ACTIVE.
create function public._active_trip_group(p_trip_id uuid)
returns uuid language plpgsql stable security definer set search_path = '' as $$
declare v_group uuid;
begin
  select group_id into v_group from public.trips where id = p_trip_id and ended_at is null;
  if v_group is null then raise exception 'TRIP_NOT_ACTIVE'; end if;
  return v_group;
end;
$$;

-- 호출한 사람이 이 운행의 운전자 행을 가졌으면 그 멤버 ID. 아니면 NOT_DRIVER.
create function public._my_driver_member_id(p_trip_id uuid)
returns uuid language plpgsql stable security definer set search_path = '' as $$
declare v_id uuid;
begin
  select tm.member_id into v_id
  from public.trip_members tm
  join public.group_members m on m.id = tm.member_id
  where tm.trip_id = p_trip_id and tm.member_id = tm.vehicle_driver_member_id and m.user_id = auth.uid();
  if v_id is null then raise exception 'NOT_DRIVER'; end if;
  return v_id;
end;
$$;

-- 6자리 초대 코드 (0/O, 1/I 제외 32종). gen_random_uuid()의 앞 6바이트는 모두 난수다.
create function public._new_invite_code()
returns text language plpgsql volatile security definer set search_path = '' as $$
declare
  v_chars constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  v_bytes bytea;
  v_code  text;
begin
  loop
    v_bytes := uuid_send(gen_random_uuid());
    v_code := '';
    for i in 0..5 loop
      v_code := v_code || substr(v_chars, get_byte(v_bytes, i) % 32 + 1, 1);
    end loop;
    exit when not exists (select 1 from public.groups where invite_code = v_code);
  end loop;
  return v_code;
end;
$$;

-- ─────────────────────────────────────────────
-- 그룹 (G1, G4, G5)
-- ─────────────────────────────────────────────

create function public.create_group(
  p_name text, p_destination_name text, p_destination_lat double precision,
  p_destination_lng double precision, p_display_name text, p_logo_url text default null
) returns public.groups language plpgsql security definer set search_path = '' as $$
declare v_group public.groups;
begin
  if auth.uid() is null then raise exception 'NOT_MEMBER'; end if;
  insert into public.groups (name, logo_url, destination_name, destination_lat, destination_lng,
                             owner_id, invite_code, invite_code_expires_at)
  values (btrim(p_name), p_logo_url, btrim(p_destination_name), p_destination_lat, p_destination_lng,
          auth.uid(), public._new_invite_code(), now() + interval '24 hours')
  returning * into v_group;
  insert into public.group_members (group_id, user_id, display_name) values (v_group.id, auth.uid(), btrim(p_display_name));
  return v_group;
end;
$$;

-- 이름·로고·목적지만 바꾼다. null로 보낸 값은 그대로 둔다.
create function public.update_group(
  p_group_id uuid, p_name text default null, p_logo_url text default null,
  p_destination_name text default null, p_destination_lat double precision default null,
  p_destination_lng double precision default null
) returns public.groups language plpgsql security definer set search_path = '' as $$
declare v_group public.groups;
begin
  perform public._require_owner(p_group_id);
  update public.groups set
    name             = coalesce(btrim(p_name), name),
    logo_url         = coalesce(p_logo_url, logo_url),
    destination_name = coalesce(btrim(p_destination_name), destination_name),
    destination_lat  = coalesce(p_destination_lat, destination_lat),
    destination_lng  = coalesce(p_destination_lng, destination_lng)
  where id = p_group_id
  returning * into v_group;
  return v_group;
end;
$$;

-- p_member_id가 없으면 본인이 나간다. 다른 사람을 빼는 건 방장만.
-- 방장은 나갈 수 없다(그룹에 방장이 없어진다) → OWNER_CANNOT_LEAVE.
create function public.leave_group(p_group_id uuid, p_member_id uuid default null)
returns void language plpgsql security definer set search_path = '' as $$
declare
  v_me     uuid := public._my_member_id(p_group_id);
  v_target uuid := coalesce(p_member_id, v_me);
  v_owner  uuid;
begin
  select owner_id into v_owner from public.groups where id = p_group_id;
  if v_target = v_me then
    if v_owner = auth.uid() then raise exception 'OWNER_CANNOT_LEAVE'; end if;
  else
    if v_owner <> auth.uid() then raise exception 'NOT_OWNER'; end if;
    if not exists (select 1 from public.group_members where id = v_target and group_id = p_group_id) then
      raise exception 'NOT_MEMBER';
    end if;
  end if;
  delete from public.group_members where id = v_target;
end;
$$;

-- ─────────────────────────────────────────────
-- 초대 (I1~I4)
-- ─────────────────────────────────────────────

create function public.reset_invite_code(p_group_id uuid)
returns table (invite_code text, invite_code_expires_at timestamptz)
language plpgsql security definer set search_path = '' as $$
begin
  perform public._require_owner(p_group_id);
  return query
    update public.groups g
    set invite_code = public._new_invite_code(), invite_code_expires_at = now() + interval '24 hours'
    where g.id = p_group_id
    returning g.invite_code, g.invite_code_expires_at;
end;
$$;

-- join_group·preview_invite 공통: 시도 제한을 확인하고 코드로 그룹을 찾는다.
-- 최근 10분 동안 실패 5회면 TOO_MANY_ATTEMPTS. 없거나 만료된 코드는 실패 1회를 남기고 INVALID_CODE.
-- 성공하면 실패 기록을 지운다. 기록은 auth.users.raw_app_meta_data (사용자가 직접 못 고친다).
create function public._find_group_by_code(p_code text, out o_group_id uuid, out o_error text)
language plpgsql security definer set search_path = '' as $$
declare
  v_meta  jsonb;
  v_count int;
  v_since timestamptz;
begin
  if auth.uid() is null then o_error := 'NOT_MEMBER'; return; end if;

  select coalesce(raw_app_meta_data, '{}'::jsonb) into v_meta from auth.users where id = auth.uid() for update;
  v_count := coalesce((v_meta ->> 'invite_fail_count')::int, 0);
  v_since := (v_meta ->> 'invite_fail_since')::timestamptz;
  if v_since is null or v_since < now() - interval '10 minutes' then
    v_count := 0;
    v_since := null;
  end if;
  if v_count >= 5 then o_error := 'TOO_MANY_ATTEMPTS'; return; end if;

  select id into o_group_id from public.groups
  where invite_code = upper(btrim(p_code)) and invite_code_expires_at > now();

  if o_group_id is null then
    update auth.users set raw_app_meta_data = v_meta
      || jsonb_build_object('invite_fail_count', v_count + 1, 'invite_fail_since', coalesce(v_since, now()))
    where id = auth.uid();
    o_error := 'INVALID_CODE';
  else
    update auth.users set raw_app_meta_data = v_meta - 'invite_fail_count' - 'invite_fail_since'
    where id = auth.uid();
  end if;
end;
$$;

-- I3: 참여 전에 그룹 이름과 인원을 보여 준다. error가 null이면 성공.
create function public.preview_invite(p_code text)
returns table (error text, group_id uuid, group_name text, member_count int)
language plpgsql security definer set search_path = '' as $$
declare v_group uuid; v_error text;
begin
  select o_group_id, o_error into v_group, v_error from public._find_group_by_code(p_code);
  if v_error is not null then
    return query select v_error, null::uuid, null::text, null::int;
    return;
  end if;
  return query
    select null::text, g.id, g.name, (select count(*)::int from public.group_members m where m.group_id = g.id)
    from public.groups g where g.id = v_group;
end;
$$;

-- I2: 코드로 참여. error가 null이면 성공.
create function public.join_group(p_code text, p_display_name text)
returns table (error text, group_id uuid, group_name text, member_count int)
language plpgsql security definer set search_path = '' as $$
declare v_group uuid; v_error text;
begin
  select o_group_id, o_error into v_group, v_error from public._find_group_by_code(p_code);
  if v_error is null and exists (select 1 from public.group_members m where m.group_id = v_group and m.user_id = auth.uid()) then
    v_error := 'ALREADY_MEMBER';
  end if;
  if v_error is not null then
    return query select v_error, null::uuid, null::text, null::int;
    return;
  end if;
  insert into public.group_members (group_id, user_id, display_name) values (v_group, auth.uid(), btrim(p_display_name));
  return query
    select null::text, g.id, g.name, (select count(*)::int from public.group_members m where m.group_id = g.id)
    from public.groups g where g.id = v_group;
end;
$$;

-- I4: 앱 없는 동승자를 이름만으로 추가한다.
create function public.add_passenger(p_group_id uuid, p_display_name text)
returns public.group_members language plpgsql security definer set search_path = '' as $$
declare v_member public.group_members;
begin
  perform public._my_member_id(p_group_id);
  insert into public.group_members (group_id, user_id, display_name) values (p_group_id, null, btrim(p_display_name))
  returning * into v_member;
  return v_member;
end;
$$;

-- ─────────────────────────────────────────────
-- 운행 (T1, T2, T4, T6, T7, T8). 채널 이름은 'trip:' || 운행 ID.
-- ─────────────────────────────────────────────

-- T1: 방장만 (합의할 것 2번 제안). 목적지를 안 주면 그룹 목적지를 복사한다.
create function public.start_trip(
  p_group_id uuid, p_destination_name text default null,
  p_destination_lat double precision default null, p_destination_lng double precision default null
) returns public.trips language plpgsql security definer set search_path = '' as $$
declare v_trip public.trips;
begin
  perform public._require_owner(p_group_id);
  insert into public.trips (group_id, started_by, destination_name, destination_lat, destination_lng)
  select g.id, auth.uid(),
         coalesce(btrim(p_destination_name), g.destination_name),
         coalesce(p_destination_lat, g.destination_lat),
         coalesce(p_destination_lng, g.destination_lng)
  from public.groups g where g.id = p_group_id
  returning * into v_trip;
  return v_trip;
exception when unique_violation then
  raise exception 'TRIP_ALREADY_ACTIVE';
end;
$$;

-- T4: 방장만.
create function public.end_trip(p_trip_id uuid)
returns timestamptz language plpgsql security definer set search_path = '' as $$
declare v_group uuid := public._active_trip_group(p_trip_id); v_ended timestamptz;
begin
  perform public._require_owner(v_group);
  update public.trips set ended_at = now() where id = p_trip_id returning ended_at into v_ended;
  return v_ended;
end;
$$;

-- T2: 내 차를 이번 운행에 등록한다. 이미 내 운전자 행이 있으면 차 이름·연료만 고친다 (앱 재시작 대비).
-- 남의 차 동승자로 이미 등록돼 있으면 ALREADY_RIDING.
create function public.join_trip(p_trip_id uuid, p_vehicle_label text, p_fuel_type text)
returns public.trip_members language plpgsql security definer set search_path = '' as $$
declare
  v_me  uuid := public._my_member_id(public._active_trip_group(p_trip_id));
  v_row public.trip_members;
begin
  insert into public.trip_members (trip_id, member_id, vehicle_driver_member_id, vehicle_label, fuel_type)
  values (p_trip_id, v_me, v_me, btrim(p_vehicle_label), p_fuel_type)
  on conflict (trip_id, member_id) do update
    set vehicle_label = excluded.vehicle_label, fuel_type = excluded.fuel_type
    where public.trip_members.vehicle_driver_member_id = public.trip_members.member_id
  returning * into v_row;
  if v_row is null then raise exception 'ALREADY_RIDING'; end if;
  return v_row;
end;
$$;

-- T6: 내 차 도착. 이미 도착했으면 처음 시각을 유지한다.
create function public.mark_arrived(p_trip_id uuid)
returns timestamptz language plpgsql security definer set search_path = '' as $$
declare v_me uuid; v_at timestamptz;
begin
  perform public._active_trip_group(p_trip_id);
  v_me := public._my_driver_member_id(p_trip_id);
  update public.trip_members set arrived_at = coalesce(arrived_at, now())
  where trip_id = p_trip_id and member_id = v_me
  returning arrived_at into v_at;
  return v_at;
end;
$$;

-- T7: 이번 운행에서 내 차에 탄 동승자 목록을 통째로 바꾼다.
create function public.set_trip_passengers(p_trip_id uuid, p_member_ids uuid[])
returns setof public.trip_members language plpgsql security definer set search_path = '' as $$
declare
  v_group uuid := public._active_trip_group(p_trip_id);
  v_me    uuid := public._my_driver_member_id(p_trip_id);
  v_id    uuid;
  v_car   uuid;
begin
  delete from public.trip_members where trip_id = p_trip_id and vehicle_driver_member_id = v_me and member_id <> v_me;

  foreach v_id in array (select coalesce(array_agg(distinct x), '{}') from unnest(p_member_ids) x) loop
    if v_id = v_me or not exists (select 1 from public.group_members where id = v_id and group_id = v_group) then
      raise exception 'INVALID_PASSENGER';
    end if;
    v_car := null;
    select vehicle_driver_member_id into v_car from public.trip_members where trip_id = p_trip_id and member_id = v_id;
    if v_car = v_id then raise exception 'INVALID_PASSENGER'; end if; -- 이번 운행에 차를 등록한 운전자
    if v_car is not null then raise exception 'PASSENGER_TAKEN'; end if; -- 다른 차에 이미 탐
    insert into public.trip_members (trip_id, member_id, vehicle_driver_member_id) values (p_trip_id, v_id, v_me);
  end loop;

  return query select * from public.trip_members
    where trip_id = p_trip_id and vehicle_driver_member_id = v_me and member_id <> v_me;
end;
$$;

-- T8: 도착한 뒤 내 차 비용을 저장한다 (다시 부르면 덮어쓴다). 연료차는 배터리 값을, 전기차는 연료 값을 0으로 보낸다.
create function public.save_trip_cost(
  p_trip_id uuid,
  p_start_fuel_ml bigint default 0, p_refueled_ml bigint default 0, p_end_fuel_ml bigint default 0,
  p_fuel_calc_method text default null, p_price_basis text default null,
  p_nearest_price integer default null, p_cheapest_price integer default null, p_applied_price integer default 0,
  p_start_battery_wh bigint default 0, p_charged_wh bigint default 0, p_end_battery_wh bigint default 0,
  p_applied_price_per_kwh integer default 0,
  p_toll_estimated integer default 0, p_toll_adjusted integer default null
) returns public.trip_members language plpgsql security definer set search_path = '' as $$
declare
  v_me  uuid := public._my_driver_member_id(p_trip_id);
  v_row public.trip_members;
begin
  if not exists (
    select 1 from public.trip_members tm join public.trips t on t.id = tm.trip_id
    where tm.trip_id = p_trip_id and tm.member_id = v_me and (tm.arrived_at is not null or t.ended_at is not null)
  ) then
    raise exception 'NOT_ARRIVED';
  end if;

  update public.trip_members set
    start_fuel_ml = p_start_fuel_ml, refueled_ml = p_refueled_ml, end_fuel_ml = p_end_fuel_ml,
    fuel_calc_method = p_fuel_calc_method, price_basis = p_price_basis,
    nearest_price = p_nearest_price, cheapest_price = p_cheapest_price, applied_price = p_applied_price,
    start_battery_wh = p_start_battery_wh, charged_wh = p_charged_wh, end_battery_wh = p_end_battery_wh,
    applied_price_per_kwh = p_applied_price_per_kwh,
    toll_estimated = p_toll_estimated, toll_adjusted = p_toll_adjusted,
    calculated_at = now()
  where trip_id = p_trip_id and member_id = v_me
  returning * into v_row;
  return v_row;
end;
$$;

-- ─────────────────────────────────────────────
-- Realtime: 위치 채널 'trip:{운행 ID}'는 private. 진행 중 운행의 운전자만 읽고 보낸다 (Broadcast·Presence).
-- ─────────────────────────────────────────────

create function public.is_active_trip_driver(p_topic text)
returns boolean language plpgsql stable security definer set search_path = '' as $$
begin
  if p_topic !~ '^trip:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    return false;
  end if;
  return exists (
    select 1
    from public.trips t
    join public.trip_members tm on tm.trip_id = t.id and tm.member_id = tm.vehicle_driver_member_id
    join public.group_members m on m.id = tm.member_id
    where t.id = substr(p_topic, 6)::uuid and t.ended_at is null and m.user_id = auth.uid()
  );
end;
$$;

create policy "진행 중 운행의 운전자만 받기" on realtime.messages for select to authenticated
  using (realtime.messages.extension in ('broadcast', 'presence') and public.is_active_trip_driver(realtime.topic()));

create policy "진행 중 운행의 운전자만 보내기" on realtime.messages for insert to authenticated
  with check (realtime.messages.extension in ('broadcast', 'presence') and public.is_active_trip_driver(realtime.topic()));

-- T5: 운행 시작·종료, 차량 도착을 Postgres Changes로 받는다 (RLS 읽기 규칙이 그대로 적용된다).
alter publication supabase_realtime add table public.trips, public.trip_members;

-- ─────────────────────────────────────────────
-- 함수 실행 권한: 로그인한 사용자(익명 포함)만
-- ─────────────────────────────────────────────

do $$
declare f regprocedure;
begin
  for f in select p.oid::regprocedure from pg_proc p where p.pronamespace = 'public'::regnamespace loop
    execute format('revoke all on function %s from public, anon', f);
    execute format('grant execute on function %s to authenticated', f);
  end loop;
end;
$$;
