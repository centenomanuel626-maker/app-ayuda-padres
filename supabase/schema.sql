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
