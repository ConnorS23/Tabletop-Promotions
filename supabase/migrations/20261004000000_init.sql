-- TableAds core schema
create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------------
-- Profiles & roles
-- ---------------------------------------------------------------------------
create type public.user_role as enum ('manager', 'super_admin');

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  email text,
  full_name text not null default '',
  role public.user_role not null default 'manager',
  created_at timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, email, full_name)
  values (new.id, new.email, coalesce(new.raw_user_meta_data ->> 'full_name', ''));
  return new;
end $$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

create or replace function public.is_super_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles where id = auth.uid() and role = 'super_admin');
$$;

-- ---------------------------------------------------------------------------
-- Restaurants & membership
-- ---------------------------------------------------------------------------
create table public.restaurants (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) > 0),
  settings_pin text not null default '1234' check (settings_pin ~ '^[0-9]{4,8}$'),
  slide_seconds int not null default 8 check (slide_seconds between 2 and 120),
  idle_timeout_seconds int not null default 60 check (idle_timeout_seconds between 10 and 3600),
  created_by uuid references auth.users (id) on delete set null default auth.uid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.restaurant_members (
  restaurant_id uuid not null references public.restaurants (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (restaurant_id, user_id)
);

create index restaurant_members_user_idx on public.restaurant_members (user_id);

create or replace function public.is_member(rid uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select public.is_super_admin()
      or exists (select 1 from public.restaurant_members where restaurant_id = rid and user_id = auth.uid());
$$;

create or replace function public.add_creator_as_member()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is not null then
    insert into public.restaurant_members (restaurant_id, user_id)
    values (new.id, auth.uid()) on conflict do nothing;
  end if;
  return new;
end $$;

create trigger restaurants_add_creator
  after insert on public.restaurants
  for each row execute function public.add_creator_as_member();

create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

create trigger restaurants_touch before update on public.restaurants
  for each row execute function public.touch_updated_at();

-- ---------------------------------------------------------------------------
-- Devices
-- ---------------------------------------------------------------------------
create table public.devices (
  id uuid primary key,
  pair_code text not null unique,
  restaurant_id uuid references public.restaurants (id) on delete set null,
  name text not null default '',
  app_version text,
  pending_command text check (pending_command in ('refresh', 'clear_cache', 'restart_app')),
  last_seen_at timestamptz,
  linked_at timestamptz,
  created_at timestamptz not null default now()
);

create index devices_restaurant_idx on public.devices (restaurant_id);

-- Secrets live in their own table with no RLS policies, so they're only reachable
-- through the security-definer RPCs below.
create table public.device_secrets (
  device_id uuid primary key references public.devices (id) on delete cascade,
  secret_hash text not null
);

-- ---------------------------------------------------------------------------
-- Ads & menus
-- ---------------------------------------------------------------------------
create table public.ads (
  id uuid primary key default gen_random_uuid(),
  restaurant_id uuid not null references public.restaurants (id) on delete cascade,
  title text not null default '',
  storage_path text not null,
  media_type text not null check (media_type in ('image', 'video')),
  duration_seconds int check (duration_seconds is null or duration_seconds between 1 and 600),
  position int not null default 0,
  active boolean not null default true,
  starts_at timestamptz,
  ends_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index ads_restaurant_idx on public.ads (restaurant_id, position);

create trigger ads_touch before update on public.ads
  for each row execute function public.touch_updated_at();

create table public.menus (
  restaurant_id uuid primary key references public.restaurants (id) on delete cascade,
  storage_path text not null,
  file_name text not null default '',
  updated_at timestamptz not null default now()
);

create trigger menus_touch before update on public.menus
  for each row execute function public.touch_updated_at();

-- Singleton row for platform-wide settings (super admin only).
create table public.app_settings (
  id int primary key default 1 check (id = 1),
  master_pin text not null default '246810' check (master_pin ~ '^[0-9]{4,8}$'),
  updated_at timestamptz not null default now()
);
insert into public.app_settings default values;

-- ---------------------------------------------------------------------------
-- Row level security
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.restaurants enable row level security;
alter table public.restaurant_members enable row level security;
alter table public.devices enable row level security;
alter table public.device_secrets enable row level security;
alter table public.ads enable row level security;
alter table public.menus enable row level security;
alter table public.app_settings enable row level security;

create policy profiles_select on public.profiles for select to authenticated using (
  id = auth.uid()
  or public.is_super_admin()
  or exists (
    select 1 from public.restaurant_members a
    join public.restaurant_members b on a.restaurant_id = b.restaurant_id
    where a.user_id = auth.uid() and b.user_id = profiles.id
  )
);
create policy profiles_update_self on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());
revoke update on public.profiles from authenticated, anon;
grant update (full_name) on public.profiles to authenticated;

create policy restaurants_select on public.restaurants for select to authenticated
  using (public.is_member(id) or created_by = auth.uid());
create policy restaurants_insert on public.restaurants for insert to authenticated with check (true);
create policy restaurants_update on public.restaurants for update to authenticated
  using (public.is_member(id)) with check (public.is_member(id));
create policy restaurants_delete on public.restaurants for delete to authenticated using (public.is_super_admin());

create policy members_select on public.restaurant_members for select to authenticated using (public.is_member(restaurant_id));
create policy members_delete on public.restaurant_members for delete to authenticated using (public.is_member(restaurant_id));

create policy devices_select on public.devices for select to authenticated
  using (public.is_super_admin() or (restaurant_id is not null and public.is_member(restaurant_id)));
create policy devices_update on public.devices for update to authenticated
  using (public.is_super_admin() or (restaurant_id is not null and public.is_member(restaurant_id)))
  with check (public.is_super_admin() or (restaurant_id is not null and public.is_member(restaurant_id)));
create policy devices_delete on public.devices for delete to authenticated using (public.is_super_admin());
revoke insert, update on public.devices from authenticated, anon;
grant update (name, pending_command) on public.devices to authenticated;

create policy ads_all on public.ads for all to authenticated
  using (public.is_member(restaurant_id)) with check (public.is_member(restaurant_id));
create policy menus_all on public.menus for all to authenticated
  using (public.is_member(restaurant_id)) with check (public.is_member(restaurant_id));

create policy app_settings_select on public.app_settings for select to authenticated using (public.is_super_admin());
create policy app_settings_update on public.app_settings for update to authenticated
  using (public.is_super_admin()) with check (public.is_super_admin());

-- ---------------------------------------------------------------------------
-- Storage: public "media" bucket, objects stored under <restaurant_id>/...
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit)
values ('media', 'media', true, 104857600)
on conflict (id) do nothing;

create or replace function public.can_manage_path(object_name text)
returns boolean language plpgsql stable security definer set search_path = public as $$
declare
  rid uuid;
begin
  begin
    rid := split_part(object_name, '/', 1)::uuid;
  exception when others then
    return false;
  end;
  return public.is_member(rid);
end $$;

create policy media_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'media' and public.can_manage_path(name));
create policy media_update on storage.objects for update to authenticated
  using (bucket_id = 'media' and public.can_manage_path(name));
create policy media_delete on storage.objects for delete to authenticated
  using (bucket_id = 'media' and public.can_manage_path(name));

-- ---------------------------------------------------------------------------
-- Manager / admin RPCs
-- ---------------------------------------------------------------------------
create or replace function public.link_device(p_pair_code text, p_restaurant_id uuid, p_name text default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  d public.devices;
begin
  if not public.is_member(p_restaurant_id) then
    raise exception 'You do not have access to this restaurant';
  end if;
  select * into d from public.devices where pair_code = upper(trim(p_pair_code));
  if not found then
    raise exception 'No device found with code %', upper(trim(p_pair_code));
  end if;
  if d.restaurant_id is not null and d.restaurant_id <> p_restaurant_id and not public.is_super_admin() then
    raise exception 'This device is already linked to another restaurant';
  end if;
  update public.devices
     set restaurant_id = p_restaurant_id,
         name = coalesce(nullif(trim(p_name), ''), name),
         linked_at = now()
   where id = d.id;
  return d.id;
end $$;

create or replace function public.unlink_device(p_device_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  rid uuid;
begin
  select restaurant_id into rid from public.devices where id = p_device_id;
  if not found then
    raise exception 'Device not found';
  end if;
  if not (public.is_super_admin() or (rid is not null and public.is_member(rid))) then
    raise exception 'Not allowed';
  end if;
  update public.devices set restaurant_id = null, linked_at = null where id = p_device_id;
end $$;

create or replace function public.add_member_by_email(p_restaurant_id uuid, p_email text)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  uid uuid;
begin
  if not public.is_member(p_restaurant_id) then
    raise exception 'Not allowed';
  end if;
  select id into uid from public.profiles where lower(email) = lower(trim(p_email));
  if uid is null then
    raise exception 'No account exists for %. Ask them to sign up first.', p_email;
  end if;
  insert into public.restaurant_members (restaurant_id, user_id) values (p_restaurant_id, uid)
  on conflict do nothing;
  return uid;
end $$;

create or replace function public.set_user_role(p_user_id uuid, p_role public.user_role)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not public.is_super_admin() then
    raise exception 'Not allowed';
  end if;
  if p_user_id = auth.uid() and p_role <> 'super_admin' then
    raise exception 'You cannot remove your own super admin role';
  end if;
  update public.profiles set role = p_role where id = p_user_id;
end $$;

-- ---------------------------------------------------------------------------
-- Device RPCs (called by the tablet app with the anon key)
-- ---------------------------------------------------------------------------
create or replace function public.generate_pair_code()
returns text language plpgsql volatile set search_path = public, extensions as $$
declare
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  code text;
begin
  loop
    code := '';
    for i in 1..6 loop
      code := code || substr(alphabet, 1 + (get_byte(gen_random_bytes(1), 0) % length(alphabet)), 1);
    end loop;
    exit when not exists (select 1 from public.devices where pair_code = code);
  end loop;
  return code;
end $$;

create or replace function public.verify_device(p_device_id uuid, p_secret text)
returns void language plpgsql stable security definer set search_path = public, extensions as $$
declare
  h text;
begin
  select secret_hash into h from public.device_secrets where device_id = p_device_id;
  if h is null or crypt(p_secret, h) <> h then
    raise exception 'invalid device credentials' using errcode = '28000';
  end if;
end $$;

create or replace function public.device_payload(p_device_id uuid, p_command text)
returns jsonb language plpgsql stable security definer set search_path = public, extensions as $$
declare
  d public.devices;
  r public.restaurants;
  master text;
  result jsonb;
begin
  select * into d from public.devices where id = p_device_id;
  select master_pin into master from public.app_settings where id = 1;

  result := jsonb_build_object(
    'device_id', d.id,
    'pair_code', d.pair_code,
    'name', d.name,
    'linked', d.restaurant_id is not null,
    'command', p_command,
    'master_pin_hash', encode(digest(d.id::text || ':' || master, 'sha256'), 'hex'),
    'server_time', now()
  );

  if d.restaurant_id is null then
    return result || jsonb_build_object('restaurant', null, 'ads', '[]'::jsonb, 'menu', null, 'pin_hash', null);
  end if;

  select * into r from public.restaurants where id = d.restaurant_id;

  return result || jsonb_build_object(
    'pin_hash', encode(digest(d.id::text || ':' || r.settings_pin, 'sha256'), 'hex'),
    'restaurant', jsonb_build_object(
      'id', r.id,
      'name', r.name,
      'slide_seconds', r.slide_seconds,
      'idle_timeout_seconds', r.idle_timeout_seconds
    ),
    'ads', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', a.id,
        'title', a.title,
        'media_type', a.media_type,
        'storage_path', a.storage_path,
        'duration_seconds', a.duration_seconds,
        'updated_at', a.updated_at
      ) order by a.position, a.created_at)
      from public.ads a
      where a.restaurant_id = r.id
        and a.active
        and (a.starts_at is null or a.starts_at <= now())
        and (a.ends_at is null or a.ends_at > now())
    ), '[]'::jsonb),
    'menu', (
      select jsonb_build_object('storage_path', m.storage_path, 'file_name', m.file_name, 'updated_at', m.updated_at)
      from public.menus m where m.restaurant_id = r.id
    )
  );
end $$;

create or replace function public.register_device(p_device_id uuid, p_secret text, p_app_version text default null)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
begin
  if p_secret is null or length(p_secret) < 32 then
    raise exception 'device secret must be at least 32 characters';
  end if;
  if exists (select 1 from public.device_secrets where device_id = p_device_id) then
    perform public.verify_device(p_device_id, p_secret);
  else
    insert into public.devices (id, pair_code) values (p_device_id, public.generate_pair_code());
    insert into public.device_secrets (device_id, secret_hash) values (p_device_id, crypt(p_secret, gen_salt('bf')));
  end if;
  update public.devices set last_seen_at = now(), app_version = coalesce(p_app_version, app_version)
   where id = p_device_id;
  return public.device_payload(p_device_id, null);
end $$;

create or replace function public.device_sync(p_device_id uuid, p_secret text, p_app_version text default null)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare
  cmd text;
begin
  perform public.verify_device(p_device_id, p_secret);
  select pending_command into cmd from public.devices where id = p_device_id for update;
  update public.devices
     set last_seen_at = now(),
         app_version = coalesce(p_app_version, app_version),
         pending_command = null
   where id = p_device_id;
  return public.device_payload(p_device_id, cmd);
end $$;

-- Lock down function execution
revoke execute on all functions in schema public from public, anon;
grant execute on function public.register_device(uuid, text, text) to anon, authenticated;
grant execute on function public.device_sync(uuid, text, text) to anon, authenticated;
grant execute on function public.is_super_admin() to authenticated;
grant execute on function public.is_member(uuid) to authenticated;
grant execute on function public.can_manage_path(text) to authenticated;
grant execute on function public.link_device(text, uuid, text) to authenticated;
grant execute on function public.unlink_device(uuid) to authenticated;
grant execute on function public.add_member_by_email(uuid, text) to authenticated;
grant execute on function public.set_user_role(uuid, public.user_role) to authenticated;
revoke execute on function public.verify_device(uuid, text) from authenticated;
revoke execute on function public.device_payload(uuid, text) from authenticated;
revoke execute on function public.generate_pair_code() from authenticated;
revoke execute on function public.handle_new_user() from authenticated;
revoke execute on function public.add_creator_as_member() from authenticated;
