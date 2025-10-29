-- Supabase schema สำหรับ Secure PWA Kiosk App

create table if not exists public.device_locations (
  id bigint generated always as identity primary key,
  device_id text not null,
  latitude double precision,
  longitude double precision,
  accuracy double precision,
  bearing double precision,
  speed double precision,
  recorded_at timestamptz default timezone('utc', now()),
  created_at timestamptz default timezone('utc', now())
);

create table if not exists public.device_status (
  id bigint generated always as identity primary key,
  device_id text not null,
  battery_percent integer,
  is_charging boolean,
  network_type text,
  connectivity text,
  kiosk_locked boolean,
  updated_at timestamptz default timezone('utc', now()),
  created_at timestamptz default timezone('utc', now())
);

create index if not exists idx_device_locations_device_id on public.device_locations(device_id);
create index if not exists idx_device_locations_recorded_at on public.device_locations(recorded_at desc);
create index if not exists idx_device_status_device_id on public.device_status(device_id);

alter table public.device_locations enable row level security;
alter table public.device_status enable row level security;

create policy "device_locations_service_role" on public.device_locations
for all using (auth.role() = 'service_role');

create policy "device_status_service_role" on public.device_status
for all using (auth.role() = 'service_role');

-- ฟังก์ชัน REST เพื่อบันทึกข้อมูล device
create or replace function public.save_device_location(payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_device_id text;
  v_lat double precision;
  v_lng double precision;
  v_accuracy double precision;
  v_bearing double precision;
  v_speed double precision;
  v_recorded timestamptz;
  v_result jsonb;
begin
  v_device_id := payload ->> 'device_id';
  v_lat := (payload ->> 'latitude')::double precision;
  v_lng := (payload ->> 'longitude')::double precision;
  v_accuracy := (payload ->> 'accuracy')::double precision;
  v_bearing := (payload ->> 'bearing')::double precision;
  v_speed := (payload ->> 'speed')::double precision;
  v_recorded := coalesce((payload ->> 'recorded_at')::timestamptz, timezone('utc', now()));

  if v_device_id is null then
    raise exception 'device_id is required';
  end if;

  insert into public.device_locations(device_id, latitude, longitude, accuracy, bearing, speed, recorded_at)
  values (v_device_id, v_lat, v_lng, v_accuracy, v_bearing, v_speed, v_recorded);

  v_result := jsonb_build_object('status', 'success');
  return v_result;
end;
$$;

grant execute on function public.save_device_location(jsonb) to service_role;

create or replace function public.save_device_status(payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_device_id text;
  v_battery integer;
  v_charging boolean;
  v_network text;
  v_connectivity text;
  v_locked boolean;
  v_result jsonb;
begin
  v_device_id := payload ->> 'device_id';
  v_battery := (payload ->> 'battery_percent')::integer;
  v_charging := (payload ->> 'is_charging')::boolean;
  v_network := payload ->> 'network_type';
  v_connectivity := payload ->> 'connectivity';
  v_locked := (payload ->> 'kiosk_locked')::boolean;

  if v_device_id is null then
    raise exception 'device_id is required';
  end if;

  insert into public.device_status(device_id, battery_percent, is_charging, network_type, connectivity, kiosk_locked)
  values (v_device_id, v_battery, v_charging, v_network, v_connectivity, v_locked);

  v_result := jsonb_build_object('status', 'success');
  return v_result;
end;
$$;

grant execute on function public.save_device_status(jsonb) to service_role;
