-- 충전 단가 applied_price_per_kwh를 정수에서 소수 둘째 자리까지로 바꾼다 (공공 충전 요금이 347.2원/kWh처럼 소수라서).
-- 정수 값은 그대로 옮겨지고, 0 이상 검사도 그대로 남는다.
alter table public.trip_members
  alter column applied_price_per_kwh type numeric(10, 2);

-- 인자 타입이 바뀌면 다른 함수가 되므로 지우고 다시 만든다. 본문은 인자 타입 말고 같다.
drop function public.save_trip_cost(uuid, bigint, bigint, bigint, text, text, integer, integer, integer,
                                    bigint, bigint, bigint, integer, integer, integer);

create function public.save_trip_cost(
  p_trip_id uuid,
  p_start_fuel_ml bigint default 0, p_refueled_ml bigint default 0, p_end_fuel_ml bigint default 0,
  p_fuel_calc_method text default null, p_price_basis text default null,
  p_nearest_price integer default null, p_cheapest_price integer default null, p_applied_price integer default 0,
  p_start_battery_wh bigint default 0, p_charged_wh bigint default 0, p_end_battery_wh bigint default 0,
  p_applied_price_per_kwh numeric(10, 2) default 0,
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

revoke all on function public.save_trip_cost(uuid, bigint, bigint, bigint, text, text, integer, integer, integer,
                                             bigint, bigint, bigint, numeric, integer, integer) from public, anon;
grant execute on function public.save_trip_cost(uuid, bigint, bigint, bigint, text, text, integer, integer, integer,
                                                bigint, bigint, bigint, numeric, integer, integer) to authenticated;
