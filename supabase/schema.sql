-- App de ayuda para mis padres: tablas en Supabase.
-- Pegar todo en Supabase > SQL Editor > New query > Run.
--
-- Seguridad: la app usa la clave pública (anon). Cada petición manda el código
-- de familia en la cabecera "x-family-code", y las reglas (RLS) solo dejan leer
-- y escribir las filas que tienen ese mismo código. El código es largo y
-- aleatorio, así que funciona como una contraseña de la familia.

create or replace function public.request_family_code()
returns text
language sql
stable
set search_path = public
as $$
  select coalesce(
    (current_setting('request.headers', true)::json ->> 'x-family-code'),
    ''
  );
$$;

-- Un registro por celular de los padres.
create table if not exists public.devices (
  id                 text primary key,
  family_code        text not null check (length(family_code) >= 8),
  label              text not null default '',
  model              text not null default '',
  battery            int,
  charging           boolean,
  network            text,
  ringer             text,
  security           text,
  last_ok            timestamptz,
  last_seen          timestamptz not null default now(),
  hidden_categories  text[] not null default '{}',
  missing            text,
  adblock            boolean
);

-- Avisos: emergencias, "Estoy bien", apps nuevas, batería baja, revisión de seguridad...
create table if not exists public.alerts (
  id            bigint generated always as identity primary key,
  family_code   text not null check (length(family_code) >= 8),
  device_id     text not null,
  device_label  text not null default '',
  kind          text not null check (kind in ('ok', 'info', 'warn', 'bad', 'emergencia')),
  text          text not null,
  lat           double precision,
  lng           double precision,
  resolved      boolean not null default false,
  created_at    timestamptz not null default now()
);

create index if not exists alerts_family_id on public.alerts (family_code, id desc);

alter table public.devices enable row level security;
alter table public.alerts  enable row level security;

drop policy if exists "familia ve sus celulares" on public.devices;
create policy "familia ve sus celulares" on public.devices
  for all to anon
  using (family_code = public.request_family_code())
  with check (family_code = public.request_family_code());

drop policy if exists "familia ve sus avisos" on public.alerts;
create policy "familia ve sus avisos" on public.alerts
  for all to anon
  using (family_code = public.request_family_code())
  with check (family_code = public.request_family_code());

grant select, insert, update on public.devices to anon;
grant select, insert, update on public.alerts  to anon;

-- Cuando llega un "Estoy bien", se guarda la hora en el celular correspondiente.
create or replace function public.alerts_after_insert()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.kind = 'ok' and new.text like 'Tocó “Estoy bien”%' then
    update public.devices set last_ok = new.created_at
      where id = new.device_id and family_code = new.family_code;
  end if;
  return new;
end;
$$;

drop trigger if exists alerts_after_insert on public.alerts;
create trigger alerts_after_insert after insert on public.alerts
  for each row execute function public.alerts_after_insert();

-- Funciones que usa el celular de Manuel (corren con las reglas de seguridad normales).
create or replace function public.resolve_alert(alert_id bigint)
returns void
language sql
security invoker
set search_path = public
as $$
  update public.alerts set resolved = true
   where id = alert_id and family_code = public.request_family_code();
$$;

create or replace function public.set_hidden_categories(target_device text, cats text[])
returns void
language sql
security invoker
set search_path = public
as $$
  update public.devices set hidden_categories = cats
   where id = target_device and family_code = public.request_family_code();
$$;

grant execute on function public.resolve_alert(bigint) to anon;
grant execute on function public.set_hidden_categories(text, text[]) to anon;
grant execute on function public.request_family_code() to anon;
revoke execute on function public.alerts_after_insert() from public, anon, authenticated;

-- Privacidad: los avisos (incluidas las ubicaciones de emergencia) se borran solos a los 30 días.
create extension if not exists pg_cron;

create or replace function public.borrar_avisos_viejos()
returns void
language sql
security definer
set search_path = public
as $$
  delete from public.alerts where created_at < now() - interval '30 days';
$$;

revoke execute on function public.borrar_avisos_viejos() from public, anon, authenticated;

select cron.schedule('borrar-avisos-viejos', '15 4 * * *', $$select public.borrar_avisos_viejos();$$);

-- Quitar un celular del panel (por ejemplo, el de prueba).
grant delete on public.devices to anon;

create or replace function public.remove_device(target_device text)
returns void
language sql
security invoker
set search_path = public
as $$
  delete from public.devices
   where id = target_device and family_code = public.request_family_code();
$$;

grant execute on function public.remove_device(text) to anon;

-- Versión instalada y resultado de "Probar todo" en cada celular.
alter table public.devices add column if not exists version text;
alter table public.devices add column if not exists diagnostic text;

-- Errores de la app (para corregirlos aunque nadie los vea). Se borran a los 30 días.
create table if not exists public.app_errors (
  id           bigint generated always as identity primary key,
  family_code  text not null check (length(family_code) >= 8),
  device_id    text not null,
  role         text,
  version      text,
  model        text,
  android      text,
  place        text,
  message      text,
  stack        text,
  happened_at  timestamptz,
  created_at   timestamptz not null default now()
);
alter table public.app_errors enable row level security;
create policy "familia reporta errores" on public.app_errors
  for insert to anon with check (family_code = public.request_family_code());
create policy "familia ve sus errores" on public.app_errors
  for select to anon using (family_code = public.request_family_code());
grant select, insert on public.app_errors to anon;

create or replace function public.borrar_avisos_viejos()
returns void
language sql
security definer
set search_path = public
as $$
  delete from public.alerts where created_at < now() - interval '30 days';
  delete from public.app_errors where created_at < now() - interval '30 days';
$$;

-- Endurecimiento (prueba de penetración): mínimo privilegio, límites de tamaño
-- y avisos que no se pueden alterar (solo marcar como resueltos).
revoke all on public.alerts, public.devices, public.app_errors from anon;
grant select, insert, update on public.alerts to anon;
grant select, insert, update, delete on public.devices to anon;
grant select, insert on public.app_errors to anon;
revoke all on public.alerts, public.devices, public.app_errors from authenticated;

alter table public.alerts
  add constraint alerts_text_len check (length(text) <= 2000),
  add constraint alerts_label_len check (length(device_label) <= 100),
  add constraint alerts_device_len check (length(device_id) <= 100);
alter table public.devices
  add constraint devices_id_len check (length(id) <= 100),
  add constraint devices_fields_len check (
    length(coalesce(label,'')) <= 100 and length(coalesce(model,'')) <= 100 and
    length(coalesce(network,'')) <= 50 and length(coalesce(ringer,'')) <= 30 and
    length(coalesce(security,'')) <= 30 and length(coalesce(missing,'')) <= 500 and
    length(coalesce(version,'')) <= 30 and length(coalesce(diagnostic,'')) <= 600 and
    coalesce(array_length(hidden_categories, 1), 0) <= 10);
alter table public.app_errors
  add constraint app_errors_len check (
    length(coalesce(message,'')) <= 1000 and length(coalesce(stack,'')) <= 8000 and
    length(coalesce(place,'')) <= 200 and length(device_id) <= 100);

create or replace function public.alerts_guard_update()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if new.text is distinct from old.text or new.kind is distinct from old.kind
     or new.lat is distinct from old.lat or new.lng is distinct from old.lng
     or new.device_id is distinct from old.device_id or new.created_at is distinct from old.created_at
     or new.family_code is distinct from old.family_code then
    raise exception 'Solo se puede marcar un aviso como resuelto';
  end if;
  return new;
end;
$$;
drop trigger if exists alerts_guard_update on public.alerts;
create trigger alerts_guard_update before update on public.alerts
  for each row execute function public.alerts_guard_update();

-- Actualización a distancia: Manuel pide una versión y el celular la instala en su próximo reporte.
alter table public.devices add column if not exists update_to int;

create or replace function public.request_update(target_device text, code int)
returns void
language sql
security invoker
set search_path = public
as $$
  update public.devices set update_to = code
   where id = target_device and family_code = public.request_family_code()
     and code between 1 and 100000;
$$;

grant execute on function public.request_update(text, int) to anon;

-- Cambiar el código de familia a distancia (ver migración codigo_familia_a_distancia en Supabase):
-- columna devices.new_family_code y funciones rotate_family_code / adopt_family_code.

-- Limpieza y Xiaomi (migración apps_y_pantalla_de_inicio):
-- home_app: nombre de la app que se adueñó de la pantalla de inicio ("" = la normal).
-- keep_alive: el aviso fijo "Ayuda está activa" está funcionando.
-- phone_setup: resumen de los ajustes de Xiaomi.
-- apps: lista compacta de apps con su uso (cada 6 horas). remove_apps: apps que Manuel pidió quitar.
alter table public.devices add column if not exists home_app text;
alter table public.devices add column if not exists keep_alive boolean;
alter table public.devices add column if not exists phone_setup text;
alter table public.devices add column if not exists apps jsonb;
alter table public.devices add column if not exists remove_apps text[];
alter table public.devices
  add constraint devices_cleanup_len check (
    length(coalesce(home_app,'')) <= 100 and length(coalesce(phone_setup,'')) <= 300 and
    (apps is null or (jsonb_typeof(apps) = 'array' and length(apps::text) <= 40000)) and
    coalesce(array_length(remove_apps, 1), 0) <= 40);

create or replace function public.request_remove_apps(target_device text, pkgs text[])
returns void
language sql
security invoker
set search_path = public
as $$
  update public.devices set remove_apps = pkgs
   where id = target_device and family_code = public.request_family_code()
     and coalesce(array_length(pkgs, 1), 0) <= 40
     and not exists (select 1 from unnest(pkgs) p where p !~ '^[A-Za-z0-9_.]{1,200}$');
$$;

grant execute on function public.request_remove_apps(text, text[]) to anon;
