-- Prepare the shared notes table. This migration must be applied before deploying
-- a client that reads or compares revision. Existing clients can omit revision.
create table public.notes (
  id text primary key,
  title text not null,
  content text not null,
  is_pinned boolean not null default false,
  created_by uuid references auth.users(id),
  updated_at timestamptz not null default now(),
  revision bigint not null default 1 check (revision > 0)
);

-- Every update, including updates from older clients, advances the revision.
-- The trigger also prevents clients from choosing the initial or next revision.
create function public.set_notes_revision()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
  if tg_op = 'INSERT' then
    new.revision := 1;
  else
    new.revision := old.revision + 1;
  end if;
  return new;
end;
$$;

create trigger notes_set_revision
before insert or update on public.notes
for each row execute function public.set_notes_revision();

alter table public.notes enable row level security;
revoke all on table public.notes from public, anon;
grant select, insert, update, delete on table public.notes to authenticated;

-- Notes are shared by active staff and admins, matching the current app model.
create policy notes_all_active_staff
on public.notes for all to authenticated
using (public.current_app_role() in ('admin', 'staff'))
with check (public.current_app_role() in ('admin', 'staff'));
