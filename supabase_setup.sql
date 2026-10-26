-- =========================================================
-- OrderApp Full Database Setup (Schema + RPCs + Seed)
-- =========================================================

-- ---------------------------------------------------------
-- MIGRATION: 202609020001_shared_cloud_schema.sql
-- ---------------------------------------------------------
create table public.products (
  id bigint primary key,
  name text not null,
  tag text,
  volume text,
  price numeric(12,2) not null check (price >= 0),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.customers (
  id text primary key,
  name text not null check (btrim(name) <> ''),
  normalized_name text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create unique index customers_normalized_name_active_uq
  on public.customers (normalized_name)
  where deleted_at is null;

create table public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  role text not null check (role in ('admin', 'staff')),
  display_name text not null default '',
  created_at timestamptz not null default now()
);

create table public.orders (
  id text primary key,
  idempotency_key text not null,
  customer_id text not null references public.customers(id),
  customer_name_snapshot text not null,
  created_at timestamptz not null default now(),
  total_qty integer not null check (total_qty >= 0),
  total numeric(12,2) not null check (total >= 0),
  status text not null default 'completed' check (status in ('completed', 'voided')),
  source_device_id text,
  created_by uuid references auth.users(id),
  deleted_at timestamptz
);

create unique index orders_idempotency_key_uq
  on public.orders (idempotency_key);

create table public.order_items (
  id text primary key,
  order_id text not null references public.orders(id) on delete cascade,
  product_id bigint references public.products(id),
  name_snapshot text not null,
  volume_snapshot text,
  unit_price numeric(12,2) not null check (unit_price >= 0),
  qty integer not null check (qty > 0),
  subtotal numeric(12,2) not null check (subtotal >= 0)
);

create table public.order_free_items (
  id text primary key,
  order_id text not null references public.orders(id) on delete cascade,
  product_id bigint not null references public.products(id),
  qty integer not null check (qty > 0)
);

create table public.order_point_uses (
  id text primary key,
  order_id text not null references public.orders(id) on delete cascade,
  product_id bigint not null references public.products(id),
  points integer not null check (points > 0)
);

create table public.point_ledger (
  id text primary key,
  idempotency_key text not null,
  customer_id text not null references public.customers(id),
  product_id bigint not null references public.products(id),
  delta integer not null,
  reason text not null check (reason in ('sale', 'redeem', 'delete_sale_reversal', 'delete_redeem_reversal', 'legacy_import')),
  order_id text references public.orders(id),
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id)
);

create unique index point_ledger_idempotency_key_uq
  on public.point_ledger (idempotency_key);

create table public.held_bills (
  id text primary key,
  customer_id text not null references public.customers(id),
  customer_name_snapshot text not null,
  note text,
  payload jsonb not null,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_by uuid references auth.users(id),
  version bigint not null default 1 check (version >= 1)
);

create table public.stock_snapshots (
  id text primary key,
  captured_at timestamptz not null default now(),
  note text,
  source_device_id text,
  created_by uuid references auth.users(id)
);

create table public.stock_snapshot_items (
  id text primary key,
  snapshot_id text not null references public.stock_snapshots(id) on delete cascade,
  product_id bigint not null references public.products(id),
  qty integer not null check (qty >= 0)
);

create table public.count_sessions (
  id text primary key,
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id),
  source_device_id text,
  image_path text,
  image_width integer check (image_width is null or image_width > 0),
  image_height integer check (image_height is null or image_height > 0),
  status text not null,
  note text,
  version bigint not null default 1 check (version >= 1)
);

create table public.count_markers (
  id text primary key,
  session_id text not null references public.count_sessions(id) on delete cascade,
  product_id bigint references public.products(id),
  x_normalized numeric not null check (x_normalized >= 0 and x_normalized <= 1),
  y_normalized numeric not null check (y_normalized >= 0 and y_normalized <= 1),
  created_at timestamptz not null default now(),
  deleted_at timestamptz
);

create view public.point_balances as
select customer_id, product_id, sum(delta)::bigint as balance
from public.point_ledger
group by customer_id, product_id;


-- ---------------------------------------------------------
-- MIGRATION: 202609020002_security_rpc_storage.sql
-- ---------------------------------------------------------
-- Shared authorization foundation for Web/PWA and Android clients.
-- Client applications authenticate through Supabase Auth and never receive a service-role key.

create or replace function public.current_app_role()
returns text
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select p.role
  from public.profiles as p
  where p.user_id = auth.uid();
$$;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select coalesce(public.current_app_role() = 'admin', false);
$$;

revoke all on function public.current_app_role() from public;
revoke all on function public.is_admin() from public;
grant execute on function public.current_app_role() to authenticated;
grant execute on function public.is_admin() to authenticated;

-- Views must evaluate the underlying table policies as the caller rather than
-- as the view owner, otherwise point balances could bypass point_ledger RLS.
alter view public.point_balances set (security_invoker = true);

-- Every public business table is protected by RLS.
alter table public.products enable row level security;
alter table public.customers enable row level security;
alter table public.profiles enable row level security;
alter table public.orders enable row level security;
alter table public.order_items enable row level security;
alter table public.order_free_items enable row level security;
alter table public.order_point_uses enable row level security;
alter table public.point_ledger enable row level security;
alter table public.held_bills enable row level security;
alter table public.stock_snapshots enable row level security;
alter table public.stock_snapshot_items enable row level security;
alter table public.count_sessions enable row level security;
alter table public.count_markers enable row level security;

-- Anonymous users have no direct access to operational data.
revoke all on table public.products from anon;
revoke all on table public.customers from anon;
revoke all on table public.profiles from anon;
revoke all on table public.orders from anon;
revoke all on table public.order_items from anon;
revoke all on table public.order_free_items from anon;
revoke all on table public.order_point_uses from anon;
revoke all on table public.point_ledger from anon;
revoke all on table public.held_bills from anon;
revoke all on table public.stock_snapshots from anon;
revoke all on table public.stock_snapshot_items from anon;
revoke all on table public.count_sessions from anon;
revoke all on table public.count_markers from anon;
revoke all on table public.point_balances from anon;

-- Authenticated application users may read shared operational data.
grant select on table public.products to authenticated;
grant select on table public.customers to authenticated;
grant select on table public.profiles to authenticated;
grant select on table public.orders to authenticated;
grant select on table public.order_items to authenticated;
grant select on table public.order_free_items to authenticated;
grant select on table public.order_point_uses to authenticated;
grant select on table public.point_ledger to authenticated;
grant select on table public.held_bills to authenticated;
grant select on table public.stock_snapshots to authenticated;
grant select on table public.stock_snapshot_items to authenticated;
grant select on table public.count_sessions to authenticated;
grant select on table public.count_markers to authenticated;
grant select on table public.point_balances to authenticated;

-- Direct customer maintenance is allowed for signed-in app staff/admin.
-- Transactional entities remain read-only to clients and are written by RPCs.
grant insert, update on table public.customers to authenticated;

-- Product maintenance is exposed to authenticated clients but RLS limits it
-- to admins. This avoids embedding privileged server credentials in either app.
grant insert, update, delete on table public.products to authenticated;

create policy products_read_authenticated
on public.products
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy products_insert_admin
on public.products
for insert
to authenticated
with check (public.is_admin());

create policy products_update_admin
on public.products
for update
to authenticated
using (public.is_admin())
with check (public.is_admin());

create policy products_delete_admin
on public.products
for delete
to authenticated
using (public.is_admin());

create policy customers_read_authenticated
on public.customers
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy customers_insert_authenticated
on public.customers
for insert
to authenticated
with check (public.current_app_role() in ('admin', 'staff'));

create policy customers_update_authenticated
on public.customers
for update
to authenticated
using (public.current_app_role() in ('admin', 'staff'))
with check (public.current_app_role() in ('admin', 'staff'));

create policy profiles_read_authenticated
on public.profiles
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy orders_read_authenticated
on public.orders
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy order_items_read_authenticated
on public.order_items
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy order_free_items_read_authenticated
on public.order_free_items
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy order_point_uses_read_authenticated
on public.order_point_uses
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy point_ledger_read_authenticated
on public.point_ledger
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy held_bills_read_authenticated
on public.held_bills
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy stock_snapshots_read_authenticated
on public.stock_snapshots
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy stock_snapshot_items_read_authenticated
on public.stock_snapshot_items
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy count_sessions_read_authenticated
on public.count_sessions
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

create policy count_markers_read_authenticated
on public.count_markers
for select
to authenticated
using (public.current_app_role() in ('admin', 'staff'));

-- Atomic sale/redemption entry point. Normal paid sales can be queued by the
-- client while offline and replayed later with the same idempotency key.
-- Point redemption is validated here against the current server balance.
create or replace function public.commit_sale(
  p_order jsonb,
  p_idempotency_key text,
  p_device_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_order_id text := btrim(coalesce(p_order ->> 'id', ''));
  v_customer_id text := btrim(coalesce(p_order ->> 'customer_id', ''));
  v_customer_name text;
  v_held_bill_id text := nullif(btrim(coalesce(p_order ->> 'held_bill_id', '')), '');
  v_items jsonb := coalesce(p_order -> 'items', '[]'::jsonb);
  v_point_uses jsonb := coalesce(p_order -> 'point_uses', '[]'::jsonb);
  v_free_items jsonb := coalesce(p_order -> 'free_items', '[]'::jsonb);
  v_item jsonb;
  v_use jsonb;
  v_free jsonb;
  v_product_id bigint;
  v_qty integer;
  v_points integer;
  v_item_id text;
  v_product_name text;
  v_product_volume text;
  v_product_price numeric(12,2);
  v_total numeric(12,2) := 0;
  v_total_qty integer := 0;
  v_balance bigint;
  v_balances jsonb;
  v_seen_paid bigint[] := array[]::bigint[];
  v_seen_uses bigint[] := array[]::bigint[];
  v_seen_free bigint[] := array[]::bigint[];
  v_seen_item_ids text[] := array[]::text[];
  v_existing public.orders%rowtype;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is null or v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;

  if jsonb_typeof(p_order) is distinct from 'object' then
    raise exception using errcode = 'P0001', message = 'invalid_order_payload';
  end if;
  if btrim(coalesce(p_idempotency_key, '')) = '' then
    raise exception using errcode = 'P0001', message = 'missing_idempotency_key';
  end if;
  if v_order_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_order_id';
  end if;
  if v_customer_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_customer_id';
  end if;
  if jsonb_typeof(v_items) is distinct from 'array'
     or jsonb_typeof(v_point_uses) is distinct from 'array'
     or jsonb_typeof(v_free_items) is distinct from 'array' then
    raise exception using errcode = 'P0001', message = 'invalid_order_arrays';
  end if;

  -- Serialize retries using the same idempotency key, then return the original
  -- committed result without inserting any duplicate rows.
  perform pg_advisory_xact_lock(hashtextextended('orderapp:sale:' || p_idempotency_key, 0));

  select * into v_existing
  from public.orders
  where idempotency_key = p_idempotency_key;

  if found then
    select coalesce(jsonb_object_agg(b.product_id::text, b.balance), '{}'::jsonb)
      into v_balances
    from (
      select l.product_id, sum(l.delta)::bigint as balance
      from public.point_ledger as l
      where l.customer_id = v_existing.customer_id
      group by l.product_id
    ) as b;

    return jsonb_build_object(
      'order_id', v_existing.id,
      'total', v_existing.total,
      'total_qty', v_existing.total_qty,
      'point_balances', coalesce(v_balances, '{}'::jsonb),
      'idempotent_replay', true
    );
  end if;

  if exists (select 1 from public.orders where id = v_order_id) then
    raise exception using errcode = 'P0001', message = 'order_id_conflict';
  end if;

  select c.name into v_customer_name
  from public.customers as c
  where c.id = v_customer_id
    and c.deleted_at is null;
  if not found then
    raise exception using errcode = 'P0001', message = 'customer_not_found';
  end if;

  if jsonb_array_length(v_point_uses) > 0 and jsonb_array_length(v_free_items) = 0 then
    raise exception using errcode = 'P0001', message = 'redemption_requires_free_item';
  end if;
  if jsonb_array_length(v_free_items) > 0 and jsonb_array_length(v_point_uses) = 0 then
    raise exception using errcode = 'P0001', message = 'free_item_requires_redemption';
  end if;
  if jsonb_array_length(v_items) = 0 and jsonb_array_length(v_point_uses) = 0 then
    raise exception using errcode = 'P0001', message = 'empty_sale';
  end if;

  if v_held_bill_id is not null then
    perform 1
    from public.held_bills as h
    where h.id = v_held_bill_id
      and h.customer_id = v_customer_id
    for update;
    if not found then
      raise exception using errcode = 'P0001', message = 'held_bill_not_found';
    end if;
  end if;

  -- Validate paid items and recompute all paid totals from the server product
  -- catalog. Client-supplied names, volumes, prices, totals, and point totals
  -- are never trusted.
  for v_item in select value from jsonb_array_elements(v_items)
  loop
    v_item_id := btrim(coalesce(v_item ->> 'id', ''));
    if v_item_id = '' then
      raise exception using errcode = 'P0001', message = 'missing_order_item_id';
    end if;
    if v_item_id = any(v_seen_item_ids) then
      raise exception using errcode = 'P0001', message = 'duplicate_order_item_id';
    end if;
    v_seen_item_ids := array_append(v_seen_item_ids, v_item_id);

    begin
      v_product_id := (v_item ->> 'product_id')::bigint;
      v_qty := (v_item ->> 'qty')::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception using errcode = 'P0001', message = 'invalid_paid_item';
    end;

    if v_product_id = any(v_seen_paid) then
      raise exception using errcode = 'P0001', message = 'duplicate_paid_product';
    end if;
    v_seen_paid := array_append(v_seen_paid, v_product_id);
    if v_qty <= 0 then
      raise exception using errcode = 'P0001', message = 'invalid_paid_quantity';
    end if;

    select p.name, p.volume, p.price
      into v_product_name, v_product_volume, v_product_price
    from public.products as p
    where p.id = v_product_id
      and p.is_active;
    if not found then
      raise exception using errcode = 'P0001', message = 'product_not_found';
    end if;

    v_total_qty := v_total_qty + v_qty;
    v_total := v_total + (v_product_price * v_qty);
  end loop;

  -- Acquire point locks in a deterministic product order so two devices cannot
  -- spend the same category balance concurrently and cannot deadlock by sending
  -- point categories in different JSON order.
  for v_product_id in
    select distinct (u.value ->> 'product_id')::bigint
    from jsonb_array_elements(v_point_uses) as u(value)
    order by 1
  loop
    perform pg_advisory_xact_lock(hashtextextended(v_customer_id || ':' || v_product_id::text, 0));
  end loop;

  for v_use in select value from jsonb_array_elements(v_point_uses)
  loop
    begin
      v_product_id := (v_use ->> 'product_id')::bigint;
      v_points := (v_use ->> 'points')::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception using errcode = 'P0001', message = 'invalid_point_use';
    end;

    if v_product_id = any(v_seen_uses) then
      raise exception using errcode = 'P0001', message = 'duplicate_point_product';
    end if;
    v_seen_uses := array_append(v_seen_uses, v_product_id);
    if v_points <= 0 then
      raise exception using errcode = 'P0001', message = 'invalid_point_use';
    end if;
    if not exists (select 1 from public.products where id = v_product_id and is_active) then
      raise exception using errcode = 'P0001', message = 'product_not_found';
    end if;

    select coalesce(sum(l.delta), 0)::bigint into v_balance
    from public.point_ledger as l
    where l.customer_id = v_customer_id
      and l.product_id = v_product_id;
    if v_points > v_balance then
      raise exception using errcode = 'P0001', message = 'insufficient_points';
    end if;
  end loop;

  for v_free in select value from jsonb_array_elements(v_free_items)
  loop
    begin
      v_product_id := (v_free ->> 'product_id')::bigint;
      v_qty := (v_free ->> 'qty')::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception using errcode = 'P0001', message = 'invalid_free_item';
    end;

    if v_product_id = any(v_seen_free) then
      raise exception using errcode = 'P0001', message = 'duplicate_free_product';
    end if;
    v_seen_free := array_append(v_seen_free, v_product_id);
    if v_qty <= 0 then
      raise exception using errcode = 'P0001', message = 'invalid_free_quantity';
    end if;
    if not exists (select 1 from public.products where id = v_product_id and is_active) then
      raise exception using errcode = 'P0001', message = 'product_not_found';
    end if;
  end loop;

  insert into public.orders (
    id, idempotency_key, customer_id, customer_name_snapshot, total_qty, total,
    status, source_device_id, created_by
  ) values (
    v_order_id, p_idempotency_key, v_customer_id, v_customer_name, v_total_qty,
    v_total, 'completed', nullif(btrim(coalesce(p_device_id, '')), ''), v_user_id
  );

  for v_item in select value from jsonb_array_elements(v_items)
  loop
    v_item_id := btrim(v_item ->> 'id');
    v_product_id := (v_item ->> 'product_id')::bigint;
    v_qty := (v_item ->> 'qty')::integer;

    select p.name, p.volume, p.price
      into v_product_name, v_product_volume, v_product_price
    from public.products as p
    where p.id = v_product_id;

    insert into public.order_items (
      id, order_id, product_id, name_snapshot, volume_snapshot, unit_price, qty, subtotal
    ) values (
      v_item_id, v_order_id, v_product_id, v_product_name, v_product_volume,
      v_product_price, v_qty, v_product_price * v_qty
    );

    insert into public.point_ledger (
      id, idempotency_key, customer_id, product_id, delta, reason, order_id, created_by
    ) values (
      v_order_id || ':ledger:sale:' || v_product_id::text,
      p_idempotency_key || ':sale:' || v_product_id::text,
      v_customer_id, v_product_id, v_qty, 'sale', v_order_id, v_user_id
    );
  end loop;

  for v_use in select value from jsonb_array_elements(v_point_uses)
  loop
    v_product_id := (v_use ->> 'product_id')::bigint;
    v_points := (v_use ->> 'points')::integer;

    insert into public.order_point_uses (id, order_id, product_id, points)
    values (
      v_order_id || ':points:' || v_product_id::text,
      v_order_id, v_product_id, v_points
    );

    insert into public.point_ledger (
      id, idempotency_key, customer_id, product_id, delta, reason, order_id, created_by
    ) values (
      v_order_id || ':ledger:redeem:' || v_product_id::text,
      p_idempotency_key || ':redeem:' || v_product_id::text,
      v_customer_id, v_product_id, -v_points, 'redeem', v_order_id, v_user_id
    );
  end loop;

  for v_free in select value from jsonb_array_elements(v_free_items)
  loop
    v_product_id := (v_free ->> 'product_id')::bigint;
    v_qty := (v_free ->> 'qty')::integer;

    insert into public.order_free_items (id, order_id, product_id, qty)
    values (
      v_order_id || ':free:' || v_product_id::text,
      v_order_id, v_product_id, v_qty
    );
  end loop;

  if v_held_bill_id is not null then
    delete from public.held_bills where id = v_held_bill_id;
  end if;

  select coalesce(jsonb_object_agg(b.product_id::text, b.balance), '{}'::jsonb)
    into v_balances
  from (
    select l.product_id, sum(l.delta)::bigint as balance
    from public.point_ledger as l
    where l.customer_id = v_customer_id
    group by l.product_id
  ) as b;

  return jsonb_build_object(
    'order_id', v_order_id,
    'total', v_total,
    'total_qty', v_total_qty,
    'point_balances', coalesce(v_balances, '{}'::jsonb),
    'idempotent_replay', false
  );
end;
$$;

revoke all on function public.commit_sale(jsonb, text, text) from public;
grant execute on function public.commit_sale(jsonb, text, text) to authenticated;


-- ---------------------------------------------------------
-- MIGRATION: 202609020003_order_reversal.sql
-- ---------------------------------------------------------
-- Admin-only order voiding. Historical sale/redeem ledger rows are immutable;
-- voiding appends exact inverse entries and marks the order as voided.

create or replace function public.void_order(
  p_order_id text,
  p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_order public.orders%rowtype;
  v_entry public.point_ledger%rowtype;
  v_original_count integer;
  v_reversal_count integer;
  v_balances jsonb;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is distinct from 'admin' then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;

  if btrim(coalesce(p_order_id, '')) = '' then
    raise exception using errcode = 'P0001', message = 'missing_order_id';
  end if;
  if btrim(coalesce(p_idempotency_key, '')) = '' then
    raise exception using errcode = 'P0001', message = 'missing_idempotency_key';
  end if;

  -- Serialize every void attempt for an order regardless of the caller's
  -- idempotency key so two admin devices cannot reverse the same order twice.
  perform pg_advisory_xact_lock(hashtextextended('orderapp:void:' || p_order_id, 0));

  select * into v_order
  from public.orders
  where id = p_order_id
  for update;

  if not found then
    raise exception using errcode = 'P0001', message = 'order_not_found';
  end if;

  select count(*)::integer into v_original_count
  from public.point_ledger as l
  where l.order_id = p_order_id
    and l.reason in ('sale', 'redeem');

  if v_original_count = 0 then
    raise exception using errcode = 'P0001', message = 'order_has_no_reversible_ledger';
  end if;

  if v_order.status = 'voided' then
    select count(*)::integer into v_reversal_count
    from public.point_ledger as r
    where r.order_id = p_order_id
      and r.reason in ('delete_sale_reversal', 'delete_redeem_reversal')
      and r.idempotency_key in (
        select p_idempotency_key || ':reversal:' || o.id
        from public.point_ledger as o
        where o.order_id = p_order_id
          and o.reason in ('sale', 'redeem')
      );

    if v_reversal_count = v_original_count then
      select coalesce(jsonb_object_agg(b.product_id::text, b.balance), '{}'::jsonb)
        into v_balances
      from (
        select l.product_id, sum(l.delta)::bigint as balance
        from public.point_ledger as l
        where l.customer_id = v_order.customer_id
        group by l.product_id
      ) as b;

      return jsonb_build_object(
        'order_id', v_order.id,
        'status', 'voided',
        'point_balances', coalesce(v_balances, '{}'::jsonb),
        'idempotent_replay', true
      );
    end if;

    raise exception using errcode = 'P0001', message = 'order_already_voided';
  end if;

  for v_entry in
    select l.*
    from public.point_ledger as l
    where l.order_id = p_order_id
      and l.reason in ('sale', 'redeem')
    order by l.id
  loop
    insert into public.point_ledger (
      id,
      idempotency_key,
      customer_id,
      product_id,
      delta,
      reason,
      order_id,
      created_by
    ) values (
      v_entry.id || ':void',
      p_idempotency_key || ':reversal:' || v_entry.id,
      v_entry.customer_id,
      v_entry.product_id,
      -v_entry.delta,
      case
        when v_entry.reason = 'sale' then 'delete_sale_reversal'
        else 'delete_redeem_reversal'
      end,
      p_order_id,
      v_user_id
    );
  end loop;

  update public.orders
  set status = 'voided',
      deleted_at = now()
  where id = p_order_id;

  select coalesce(jsonb_object_agg(b.product_id::text, b.balance), '{}'::jsonb)
    into v_balances
  from (
    select l.product_id, sum(l.delta)::bigint as balance
    from public.point_ledger as l
    where l.customer_id = v_order.customer_id
    group by l.product_id
  ) as b;

  return jsonb_build_object(
    'order_id', p_order_id,
    'status', 'voided',
    'point_balances', coalesce(v_balances, '{}'::jsonb),
    'idempotent_replay', false
  );
end;
$$;

revoke all on function public.void_order(text, text) from public;
grant execute on function public.void_order(text, text) to authenticated;


-- ---------------------------------------------------------
-- MIGRATION: 202609020004_operational_rpc_storage.sql
-- ---------------------------------------------------------
-- Shared operational RPC surface for customers, held bills, stock snapshots,
-- image-counting sessions/markers, and the private count-images bucket.

-- Stock snapshots are append-only, but retryable client writes need a durable
-- operation key so a reconnect/replay cannot create a duplicate count round.
alter table public.stock_snapshots
  add column idempotency_key text;

create unique index stock_snapshots_idempotency_key_uq
  on public.stock_snapshots (idempotency_key)
  where idempotency_key is not null;

create or replace function public.create_customer(
  p_id text,
  p_name text
)
returns public.customers
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_id text := btrim(coalesce(p_id, ''));
  v_name text := btrim(coalesce(p_name, ''));
  v_customer public.customers%rowtype;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is null or v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;
  if v_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_customer_id';
  end if;
  if v_name = '' then
    raise exception using errcode = 'P0001', message = 'missing_customer_name';
  end if;

  insert into public.customers (id, name, normalized_name)
  values (v_id, v_name, lower(v_name))
  returning * into v_customer;

  return v_customer;
end;
$$;

create or replace function public.save_held_bill(
  p_bill jsonb,
  p_expected_version bigint
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_id text := btrim(coalesce(p_bill ->> 'id', ''));
  v_customer_id text := btrim(coalesce(p_bill ->> 'customer_id', ''));
  v_customer_name text;
  v_version bigint;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is null or v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;
  if jsonb_typeof(p_bill) is distinct from 'object' then
    raise exception using errcode = 'P0001', message = 'invalid_held_bill_payload';
  end if;
  if v_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_held_bill_id';
  end if;
  if v_customer_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_customer_id';
  end if;
  if p_expected_version is null or p_expected_version < 0 then
    raise exception using errcode = 'P0001', message = 'invalid_expected_version';
  end if;

  select c.name into v_customer_name
  from public.customers as c
  where c.id = v_customer_id
    and c.deleted_at is null;
  if not found then
    raise exception using errcode = 'P0001', message = 'customer_not_found';
  end if;

  if p_expected_version = 0 then
    begin
      insert into public.held_bills (
        id,
        customer_id,
        customer_name_snapshot,
        note,
        payload,
        updated_by,
        version
      ) values (
        v_id,
        v_customer_id,
        v_customer_name,
        nullif(p_bill ->> 'note', ''),
        coalesce(p_bill -> 'payload', '{}'::jsonb),
        v_user_id,
        1
      )
      returning version into v_version;
    exception when unique_violation then
      raise exception using errcode = '40001', message = 'held_bill_version_conflict';
    end;
  else
    update public.held_bills as h
    set customer_id = v_customer_id,
        customer_name_snapshot = v_customer_name,
        note = case when p_bill ? 'note' then nullif(p_bill ->> 'note', '') else h.note end,
        payload = case when p_bill ? 'payload' then coalesce(p_bill -> 'payload', '{}'::jsonb) else h.payload end,
        updated_at = now(),
        updated_by = v_user_id,
        version = h.version + 1
    where h.id = v_id
      and h.version = p_expected_version
    returning h.version into v_version;

    if not found then
      raise exception using errcode = '40001', message = 'held_bill_version_conflict';
    end if;
  end if;

  return jsonb_build_object('id', v_id, 'version', v_version);
end;
$$;

create or replace function public.delete_held_bill(
  p_id text,
  p_expected_version bigint
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_id text := btrim(coalesce(p_id, ''));
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is null or v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;
  if v_id = '' or p_expected_version is null or p_expected_version < 1 then
    raise exception using errcode = 'P0001', message = 'invalid_held_bill_delete';
  end if;

  delete from public.held_bills as h
  where h.id = v_id
    and h.version = p_expected_version;

  if not found then
    raise exception using errcode = '40001', message = 'held_bill_version_conflict';
  end if;
end;
$$;

create or replace function public.create_stock_snapshot(
  p_snapshot jsonb,
  p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_id text := btrim(coalesce(p_snapshot ->> 'id', ''));
  v_key text := btrim(coalesce(p_idempotency_key, ''));
  v_existing_id text;
  v_items jsonb := coalesce(p_snapshot -> 'items', '[]'::jsonb);
  v_item jsonb;
  v_item_id text;
  v_product_id bigint;
  v_qty integer;
  v_captured_at timestamptz := now();
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is null or v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;
  if jsonb_typeof(p_snapshot) is distinct from 'object' then
    raise exception using errcode = 'P0001', message = 'invalid_stock_payload';
  end if;
  if v_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_stock_snapshot_id';
  end if;
  if v_key = '' then
    raise exception using errcode = 'P0001', message = 'missing_idempotency_key';
  end if;
  if jsonb_typeof(v_items) is distinct from 'array' then
    raise exception using errcode = 'P0001', message = 'invalid_stock_items';
  end if;

  perform pg_advisory_xact_lock(hashtextextended('orderapp:stock:' || v_key, 0));

  select s.id into v_existing_id
  from public.stock_snapshots as s
  where s.idempotency_key = v_key;

  if found then
    return jsonb_build_object('snapshot_id', v_existing_id, 'idempotent_replay', true);
  end if;

  if exists (select 1 from public.stock_snapshots where id = v_id) then
    raise exception using errcode = 'P0001', message = 'stock_snapshot_id_conflict';
  end if;

  if nullif(btrim(coalesce(p_snapshot ->> 'captured_at', '')), '') is not null then
    v_captured_at := (p_snapshot ->> 'captured_at')::timestamptz;
  end if;

  insert into public.stock_snapshots (
    id,
    captured_at,
    note,
    source_device_id,
    created_by,
    idempotency_key
  ) values (
    v_id,
    v_captured_at,
    nullif(p_snapshot ->> 'note', ''),
    nullif(p_snapshot ->> 'source_device_id', ''),
    v_user_id,
    v_key
  );

  for v_item in select value from jsonb_array_elements(v_items)
  loop
    v_item_id := btrim(coalesce(v_item ->> 'id', ''));
    if v_item_id = '' then
      raise exception using errcode = 'P0001', message = 'missing_stock_item_id';
    end if;

    v_product_id := (v_item ->> 'product_id')::bigint;
    v_qty := (v_item ->> 'qty')::integer;

    insert into public.stock_snapshot_items (id, snapshot_id, product_id, qty)
    values (v_item_id, v_id, v_product_id, v_qty);
  end loop;

  return jsonb_build_object('snapshot_id', v_id, 'idempotent_replay', false);
end;
$$;

create or replace function public.save_count_session(
  p_session jsonb,
  p_expected_version bigint
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_id text := btrim(coalesce(p_session ->> 'id', ''));
  v_status text := btrim(coalesce(p_session ->> 'status', ''));
  v_version bigint;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is null or v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;
  if jsonb_typeof(p_session) is distinct from 'object' then
    raise exception using errcode = 'P0001', message = 'invalid_count_session_payload';
  end if;
  if v_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_count_session_id';
  end if;
  if v_status = '' then
    raise exception using errcode = 'P0001', message = 'missing_count_session_status';
  end if;
  if p_expected_version is null or p_expected_version < 0 then
    raise exception using errcode = 'P0001', message = 'invalid_expected_version';
  end if;

  if p_expected_version = 0 then
    begin
      insert into public.count_sessions (
        id,
        created_by,
        source_device_id,
        image_path,
        image_width,
        image_height,
        status,
        note,
        version
      ) values (
        v_id,
        v_user_id,
        nullif(p_session ->> 'source_device_id', ''),
        nullif(p_session ->> 'image_path', ''),
        case when p_session ? 'image_width' then (p_session ->> 'image_width')::integer else null end,
        case when p_session ? 'image_height' then (p_session ->> 'image_height')::integer else null end,
        v_status,
        nullif(p_session ->> 'note', ''),
        1
      )
      returning version into v_version;
    exception when unique_violation then
      raise exception using errcode = '40001', message = 'count_session_version_conflict';
    end;
  else
    update public.count_sessions as s
    set source_device_id = case when p_session ? 'source_device_id' then nullif(p_session ->> 'source_device_id', '') else s.source_device_id end,
        image_path = case when p_session ? 'image_path' then nullif(p_session ->> 'image_path', '') else s.image_path end,
        image_width = case when p_session ? 'image_width' then (p_session ->> 'image_width')::integer else s.image_width end,
        image_height = case when p_session ? 'image_height' then (p_session ->> 'image_height')::integer else s.image_height end,
        status = v_status,
        note = case when p_session ? 'note' then nullif(p_session ->> 'note', '') else s.note end,
        version = s.version + 1
    where s.id = v_id
      and s.version = p_expected_version
    returning s.version into v_version;

    if not found then
      raise exception using errcode = '40001', message = 'count_session_version_conflict';
    end if;
  end if;

  return jsonb_build_object('id', v_id, 'version', v_version);
end;
$$;

create or replace function public.add_count_marker(
  p_marker jsonb
)
returns public.count_markers
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_id text := btrim(coalesce(p_marker ->> 'id', ''));
  v_session_id text := btrim(coalesce(p_marker ->> 'session_id', ''));
  v_marker public.count_markers%rowtype;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is null or v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;
  if jsonb_typeof(p_marker) is distinct from 'object' then
    raise exception using errcode = 'P0001', message = 'invalid_count_marker_payload';
  end if;
  if v_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_count_marker_id';
  end if;
  if v_session_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_count_session_id';
  end if;

  insert into public.count_markers (
    id,
    session_id,
    product_id,
    x_normalized,
    y_normalized
  ) values (
    v_id,
    v_session_id,
    case when nullif(btrim(coalesce(p_marker ->> 'product_id', '')), '') is null then null else (p_marker ->> 'product_id')::bigint end,
    (p_marker ->> 'x_normalized')::numeric,
    (p_marker ->> 'y_normalized')::numeric
  )
  returning * into v_marker;

  return v_marker;
end;
$$;

create or replace function public.delete_count_marker(
  p_id text
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_id text := btrim(coalesce(p_id, ''));
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is null or v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;
  if v_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_count_marker_id';
  end if;

  update public.count_markers
  set deleted_at = coalesce(deleted_at, now())
  where id = v_id;
end;
$$;

revoke all on function public.create_customer(text, text) from public;
revoke all on function public.save_held_bill(jsonb, bigint) from public;
revoke all on function public.delete_held_bill(text, bigint) from public;
revoke all on function public.create_stock_snapshot(jsonb, text) from public;
revoke all on function public.save_count_session(jsonb, bigint) from public;
revoke all on function public.add_count_marker(jsonb) from public;
revoke all on function public.delete_count_marker(text) from public;

grant execute on function public.create_customer(text, text) to authenticated;
grant execute on function public.save_held_bill(jsonb, bigint) to authenticated;
grant execute on function public.delete_held_bill(text, bigint) to authenticated;
grant execute on function public.create_stock_snapshot(jsonb, text) to authenticated;
grant execute on function public.save_count_session(jsonb, bigint) to authenticated;
grant execute on function public.add_count_marker(jsonb) to authenticated;
grant execute on function public.delete_count_marker(text) to authenticated;

-- Images are never public. Clients use authenticated Storage requests/signed
-- access and the database only stores paths/metadata.
insert into storage.buckets (id, name, public)
values ('count-images', 'count-images', false)
on conflict (id) do update
set public = false;

create policy count_images_read_authenticated
on storage.objects
for select
to authenticated
using (
  bucket_id = 'count-images'
  and auth.role() = 'authenticated'
  and public.current_app_role() in ('staff', 'admin')
);

create policy count_images_insert_authenticated
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'count-images'
  and auth.role() = 'authenticated'
  and public.current_app_role() in ('staff', 'admin')
);

create policy count_images_update_authenticated
on storage.objects
for update
to authenticated
using (
  bucket_id = 'count-images'
  and auth.role() = 'authenticated'
  and public.current_app_role() in ('staff', 'admin')
)
with check (
  bucket_id = 'count-images'
  and auth.role() = 'authenticated'
  and public.current_app_role() in ('staff', 'admin')
);

create policy count_images_delete_authenticated
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'count-images'
  and auth.role() = 'authenticated'
  and public.current_app_role() in ('staff', 'admin')
);


-- ---------------------------------------------------------
-- MIGRATION: 202609030001_legacy_import_rpc.sql
-- ---------------------------------------------------------
-- Admin-only import surface used once by the Android legacy migration.
-- It preserves historical entity IDs/timestamps without exposing direct writes
-- to transactional tables or requiring a service-role key in the client.

create or replace function public.import_legacy_record(
  p_kind text,
  p_payload jsonb,
  p_idempotency_key text
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_kind text := lower(btrim(coalesce(p_kind, '')));
  v_key text := btrim(coalesce(p_idempotency_key, ''));
  v_id text := btrim(coalesce(p_payload ->> 'id', ''));
  v_customer_id text;
  v_customer_name text;
  v_name text;
  v_created_at timestamptz;
  v_updated_at timestamptz;
  v_reason text;
  v_status text;
  v_item jsonb;
  v_existing_id text;
  v_existing_key text;
  v_version bigint;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is distinct from 'admin' then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;

  if jsonb_typeof(p_payload) is distinct from 'object' then
    raise exception using errcode = 'P0001', message = 'invalid_legacy_payload';
  end if;
  if v_kind = '' then
    raise exception using errcode = 'P0001', message = 'missing_legacy_kind';
  end if;
  if v_key = '' then
    raise exception using errcode = 'P0001', message = 'missing_idempotency_key';
  end if;
  if v_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_legacy_id';
  end if;

  perform pg_advisory_xact_lock(hashtextextended('orderapp:legacy:' || v_kind || ':' || v_id, 0));

  case v_kind
    when 'customer' then
      v_name := btrim(coalesce(p_payload ->> 'name', ''));
      if v_name = '' then
        raise exception using errcode = 'P0001', message = 'missing_customer_name';
      end if;
      v_created_at := coalesce(nullif(p_payload ->> 'created_at', '')::timestamptz, now());

      select c.id into v_existing_id
      from public.customers as c
      where c.id = v_id;
      if found then
        if exists (
          select 1 from public.customers as c
          where c.id = v_id
            and lower(btrim(c.name)) <> lower(v_name)
        ) then
          raise exception using errcode = 'P0001', message = 'legacy_id_conflict';
        end if;
        return jsonb_build_object('id', v_id, 'idempotent_replay', true);
      end if;

      insert into public.customers (
        id, name, normalized_name, created_at, updated_at
      ) values (
        v_id, v_name, lower(v_name), v_created_at, v_created_at
      );

      return jsonb_build_object('id', v_id, 'idempotent_replay', false);

    when 'order' then
      select o.id, o.idempotency_key into v_existing_id, v_existing_key
      from public.orders as o
      where o.idempotency_key = v_key;
      if found then
        if v_existing_id <> v_id then
          raise exception using errcode = 'P0001', message = 'legacy_id_conflict';
        end if;
        return jsonb_build_object('id', v_id, 'idempotent_replay', true);
      end if;

      select o.idempotency_key into v_existing_key
      from public.orders as o
      where o.id = v_id;
      if found then
        raise exception using errcode = 'P0001', message = 'legacy_id_conflict';
      end if;

      v_customer_id := btrim(coalesce(p_payload ->> 'customer_id', ''));
      if v_customer_id = '' then
        raise exception using errcode = 'P0001', message = 'missing_customer_id';
      end if;
      select c.name into v_customer_name
      from public.customers as c
      where c.id = v_customer_id and c.deleted_at is null;
      if not found then
        raise exception using errcode = 'P0001', message = 'customer_not_found';
      end if;
      v_customer_name := btrim(coalesce(nullif(p_payload ->> 'customer_name_snapshot', ''), v_customer_name));
      v_created_at := coalesce(nullif(p_payload ->> 'created_at', '')::timestamptz, now());
      v_status := coalesce(nullif(btrim(p_payload ->> 'status'), ''), 'completed');
      if v_status not in ('completed', 'voided') then
        raise exception using errcode = 'P0001', message = 'invalid_order_status';
      end if;

      if jsonb_typeof(coalesce(p_payload -> 'items', '[]'::jsonb)) is distinct from 'array'
         or jsonb_typeof(coalesce(p_payload -> 'free_items', '[]'::jsonb)) is distinct from 'array'
         or jsonb_typeof(coalesce(p_payload -> 'point_uses', '[]'::jsonb)) is distinct from 'array' then
        raise exception using errcode = 'P0001', message = 'invalid_order_arrays';
      end if;

      insert into public.orders (
        id, idempotency_key, customer_id, customer_name_snapshot,
        created_at, total_qty, total, status, source_device_id, created_by,
        deleted_at
      ) values (
        v_id,
        v_key,
        v_customer_id,
        v_customer_name,
        v_created_at,
        coalesce((p_payload ->> 'total_qty')::integer, 0),
        coalesce((p_payload ->> 'total')::numeric, 0),
        v_status,
        nullif(btrim(coalesce(p_payload ->> 'source_device_id', '')), ''),
        v_user_id,
        nullif(p_payload ->> 'deleted_at', '')::timestamptz
      );

      for v_item in
        select value from jsonb_array_elements(coalesce(p_payload -> 'items', '[]'::jsonb))
      loop
        if btrim(coalesce(v_item ->> 'id', '')) = '' then
          raise exception using errcode = 'P0001', message = 'missing_order_item_id';
        end if;
        insert into public.order_items (
          id, order_id, product_id, name_snapshot, volume_snapshot,
          unit_price, qty, subtotal
        ) values (
          btrim(v_item ->> 'id'),
          v_id,
          (v_item ->> 'product_id')::bigint,
          coalesce(nullif(v_item ->> 'name_snapshot', ''), ''),
          nullif(v_item ->> 'volume_snapshot', ''),
          (v_item ->> 'unit_price')::numeric,
          (v_item ->> 'qty')::integer,
          (v_item ->> 'subtotal')::numeric
        );
      end loop;

      for v_item in
        select value from jsonb_array_elements(coalesce(p_payload -> 'free_items', '[]'::jsonb))
      loop
        if btrim(coalesce(v_item ->> 'id', '')) = '' then
          raise exception using errcode = 'P0001', message = 'missing_free_item_id';
        end if;
        insert into public.order_free_items (id, order_id, product_id, qty)
        values (
          btrim(v_item ->> 'id'),
          v_id,
          (v_item ->> 'product_id')::bigint,
          (v_item ->> 'qty')::integer
        );
      end loop;

      for v_item in
        select value from jsonb_array_elements(coalesce(p_payload -> 'point_uses', '[]'::jsonb))
      loop
        if btrim(coalesce(v_item ->> 'id', '')) = '' then
          raise exception using errcode = 'P0001', message = 'missing_point_use_id';
        end if;
        insert into public.order_point_uses (id, order_id, product_id, points)
        values (
          btrim(v_item ->> 'id'),
          v_id,
          (v_item ->> 'product_id')::bigint,
          (v_item ->> 'points')::integer
        );
      end loop;

      return jsonb_build_object('id', v_id, 'idempotent_replay', false);

    when 'point_ledger' then
      select l.id into v_existing_id
      from public.point_ledger as l
      where l.idempotency_key = v_key;
      if found then
        if v_existing_id <> v_id then
          raise exception using errcode = 'P0001', message = 'legacy_id_conflict';
        end if;
        return jsonb_build_object('id', v_id, 'idempotent_replay', true);
      end if;
      if exists(select 1 from public.point_ledger as l where l.id = v_id) then
        raise exception using errcode = 'P0001', message = 'legacy_id_conflict';
      end if;

      v_customer_id := btrim(coalesce(p_payload ->> 'customer_id', ''));
      v_reason := lower(btrim(coalesce(p_payload ->> 'reason', 'legacy_import')));
      if v_reason not in ('sale', 'redeem', 'delete_sale_reversal', 'delete_redeem_reversal', 'legacy_import') then
        v_reason := 'legacy_import';
      end if;
      v_created_at := coalesce(nullif(p_payload ->> 'created_at', '')::timestamptz, now());

      insert into public.point_ledger (
        id, idempotency_key, customer_id, product_id, delta, reason,
        order_id, created_at, created_by
      ) values (
        v_id,
        v_key,
        v_customer_id,
        (p_payload ->> 'product_id')::bigint,
        (p_payload ->> 'delta')::integer,
        v_reason,
        nullif(btrim(coalesce(p_payload ->> 'order_id', '')), ''),
        v_created_at,
        v_user_id
      );

      return jsonb_build_object('id', v_id, 'idempotent_replay', false);

    when 'held_bill' then
      select h.id into v_existing_id
      from public.held_bills as h
      where h.id = v_id;
      if found then
        return jsonb_build_object('id', v_id, 'idempotent_replay', true);
      end if;

      v_customer_id := btrim(coalesce(p_payload ->> 'customer_id', ''));
      select c.name into v_customer_name
      from public.customers as c
      where c.id = v_customer_id and c.deleted_at is null;
      if not found then
        raise exception using errcode = 'P0001', message = 'customer_not_found';
      end if;
      v_customer_name := btrim(coalesce(nullif(p_payload ->> 'customer_name_snapshot', ''), v_customer_name));
      v_created_at := coalesce(nullif(p_payload ->> 'created_at', '')::timestamptz, now());
      v_updated_at := coalesce(nullif(p_payload ->> 'updated_at', '')::timestamptz, v_created_at);
      v_version := greatest(coalesce((p_payload ->> 'version')::bigint, 1), 1);

      insert into public.held_bills (
        id, customer_id, customer_name_snapshot, note, payload,
        updated_at, created_at, updated_by, version
      ) values (
        v_id,
        v_customer_id,
        v_customer_name,
        nullif(p_payload ->> 'note', ''),
        coalesce(p_payload -> 'payload', '{}'::jsonb),
        v_updated_at,
        v_created_at,
        v_user_id,
        v_version
      );

      return jsonb_build_object('id', v_id, 'idempotent_replay', false);

    when 'stock_snapshot' then
      select s.id into v_existing_id
      from public.stock_snapshots as s
      where s.idempotency_key = v_key;
      if found then
        if v_existing_id <> v_id then
          raise exception using errcode = 'P0001', message = 'legacy_id_conflict';
        end if;
        return jsonb_build_object('id', v_id, 'idempotent_replay', true);
      end if;
      if exists(select 1 from public.stock_snapshots as s where s.id = v_id) then
        raise exception using errcode = 'P0001', message = 'legacy_id_conflict';
      end if;
      if jsonb_typeof(coalesce(p_payload -> 'items', '[]'::jsonb)) is distinct from 'array' then
        raise exception using errcode = 'P0001', message = 'invalid_stock_items';
      end if;

      v_created_at := coalesce(nullif(p_payload ->> 'captured_at', '')::timestamptz, now());
      insert into public.stock_snapshots (
        id, captured_at, note, source_device_id, created_by, idempotency_key
      ) values (
        v_id,
        v_created_at,
        nullif(p_payload ->> 'note', ''),
        nullif(btrim(coalesce(p_payload ->> 'source_device_id', '')), ''),
        v_user_id,
        v_key
      );

      for v_item in
        select value from jsonb_array_elements(coalesce(p_payload -> 'items', '[]'::jsonb))
      loop
        if btrim(coalesce(v_item ->> 'id', '')) = '' then
          raise exception using errcode = 'P0001', message = 'missing_stock_item_id';
        end if;
        insert into public.stock_snapshot_items (id, snapshot_id, product_id, qty)
        values (
          btrim(v_item ->> 'id'),
          v_id,
          (v_item ->> 'product_id')::bigint,
          (v_item ->> 'qty')::integer
        );
      end loop;

      return jsonb_build_object('id', v_id, 'idempotent_replay', false);

    else
      raise exception using errcode = 'P0001', message = 'unsupported_legacy_kind';
  end case;
end;
$$;

create or replace function public.verify_legacy_import(p_manifest jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_result jsonb;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is distinct from 'admin' then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;
  if jsonb_typeof(p_manifest) is distinct from 'object' then
    raise exception using errcode = 'P0001', message = 'invalid_legacy_manifest';
  end if;

  select jsonb_build_object(
    'customers', (
      select count(*)::int from public.customers as t
      where t.id in (select jsonb_array_elements_text(coalesce(p_manifest -> 'customers', '[]'::jsonb)))
    ),
    'orders', (
      select count(*)::int from public.orders as t
      where t.id in (select jsonb_array_elements_text(coalesce(p_manifest -> 'orders', '[]'::jsonb)))
    ),
    'order_items', (
      select count(*)::int from public.order_items as t
      where t.id in (select jsonb_array_elements_text(coalesce(p_manifest -> 'order_items', '[]'::jsonb)))
    ),
    'order_free_items', (
      select count(*)::int from public.order_free_items as t
      where t.id in (select jsonb_array_elements_text(coalesce(p_manifest -> 'order_free_items', '[]'::jsonb)))
    ),
    'order_point_uses', (
      select count(*)::int from public.order_point_uses as t
      where t.id in (select jsonb_array_elements_text(coalesce(p_manifest -> 'order_point_uses', '[]'::jsonb)))
    ),
    'point_ledger', (
      select count(*)::int from public.point_ledger as t
      where t.id in (select jsonb_array_elements_text(coalesce(p_manifest -> 'point_ledger', '[]'::jsonb)))
    ),
    'held_bills', (
      select count(*)::int from public.held_bills as t
      where t.id in (select jsonb_array_elements_text(coalesce(p_manifest -> 'held_bills', '[]'::jsonb)))
    ),
    'stock_snapshots', (
      select count(*)::int from public.stock_snapshots as t
      where t.id in (select jsonb_array_elements_text(coalesce(p_manifest -> 'stock_snapshots', '[]'::jsonb)))
    ),
    'stock_snapshot_items', (
      select count(*)::int from public.stock_snapshot_items as t
      where t.id in (select jsonb_array_elements_text(coalesce(p_manifest -> 'stock_snapshot_items', '[]'::jsonb)))
    )
  ) into v_result;

  return v_result;
end;
$$;

revoke all on function public.import_legacy_record(text, jsonb, text) from public;
revoke all on function public.verify_legacy_import(jsonb) from public;
grant execute on function public.import_legacy_record(text, jsonb, text) to authenticated;
grant execute on function public.verify_legacy_import(jsonb) to authenticated;


-- ---------------------------------------------------------
-- MIGRATION: 202609030002_count_retry_idempotency.sql
-- ---------------------------------------------------------
-- Make image-count creation safe to replay after a client loses the response
-- after the server has already committed. Exact replays succeed; reused IDs
-- with different payloads still conflict.

create or replace function public.save_count_session(
  p_session jsonb,
  p_expected_version bigint
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_id text := btrim(coalesce(p_session ->> 'id', ''));
  v_status text := btrim(coalesce(p_session ->> 'status', ''));
  v_source_device_id text := nullif(p_session ->> 'source_device_id', '');
  v_image_path text := nullif(p_session ->> 'image_path', '');
  v_image_width integer := case when p_session ? 'image_width' then (p_session ->> 'image_width')::integer else null end;
  v_image_height integer := case when p_session ? 'image_height' then (p_session ->> 'image_height')::integer else null end;
  v_note text := nullif(p_session ->> 'note', '');
  v_version bigint;
  v_existing public.count_sessions%rowtype;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is null or v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;
  if jsonb_typeof(p_session) is distinct from 'object' then
    raise exception using errcode = 'P0001', message = 'invalid_count_session_payload';
  end if;
  if v_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_count_session_id';
  end if;
  if v_status = '' then
    raise exception using errcode = 'P0001', message = 'missing_count_session_status';
  end if;
  if p_expected_version is null or p_expected_version < 0 then
    raise exception using errcode = 'P0001', message = 'invalid_expected_version';
  end if;

  if p_expected_version = 0 then
    perform pg_advisory_xact_lock(hashtextextended('orderapp:count-session:' || v_id, 0));

    select * into v_existing
    from public.count_sessions
    where id = v_id;

    if found then
      if v_existing.source_device_id is not distinct from v_source_device_id
         and v_existing.image_path is not distinct from v_image_path
         and v_existing.image_width is not distinct from v_image_width
         and v_existing.image_height is not distinct from v_image_height
         and v_existing.status = v_status
         and v_existing.note is not distinct from v_note then
        return jsonb_build_object('id', v_existing.id, 'version', v_existing.version);
      end if;
      raise exception using errcode = '40001', message = 'count_session_version_conflict';
    end if;

    insert into public.count_sessions (
      id,
      created_by,
      source_device_id,
      image_path,
      image_width,
      image_height,
      status,
      note,
      version
    ) values (
      v_id,
      v_user_id,
      v_source_device_id,
      v_image_path,
      v_image_width,
      v_image_height,
      v_status,
      v_note,
      1
    )
    returning version into v_version;
  else
    update public.count_sessions as s
    set source_device_id = case when p_session ? 'source_device_id' then v_source_device_id else s.source_device_id end,
        image_path = case when p_session ? 'image_path' then v_image_path else s.image_path end,
        image_width = case when p_session ? 'image_width' then v_image_width else s.image_width end,
        image_height = case when p_session ? 'image_height' then v_image_height else s.image_height end,
        status = v_status,
        note = case when p_session ? 'note' then v_note else s.note end,
        version = s.version + 1
    where s.id = v_id
      and s.version = p_expected_version
    returning s.version into v_version;

    if not found then
      raise exception using errcode = '40001', message = 'count_session_version_conflict';
    end if;
  end if;

  return jsonb_build_object('id', v_id, 'version', v_version);
end;
$$;

create or replace function public.add_count_marker(
  p_marker jsonb
)
returns public.count_markers
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_id text := btrim(coalesce(p_marker ->> 'id', ''));
  v_session_id text := btrim(coalesce(p_marker ->> 'session_id', ''));
  v_product_id bigint := case
    when nullif(btrim(coalesce(p_marker ->> 'product_id', '')), '') is null then null
    else (p_marker ->> 'product_id')::bigint
  end;
  v_x numeric := (p_marker ->> 'x_normalized')::numeric;
  v_y numeric := (p_marker ->> 'y_normalized')::numeric;
  v_marker public.count_markers%rowtype;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id;

  if v_role is null or v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;
  if jsonb_typeof(p_marker) is distinct from 'object' then
    raise exception using errcode = 'P0001', message = 'invalid_count_marker_payload';
  end if;
  if v_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_count_marker_id';
  end if;
  if v_session_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_count_session_id';
  end if;

  perform pg_advisory_xact_lock(hashtextextended('orderapp:count-marker:' || v_id, 0));

  select * into v_marker
  from public.count_markers
  where id = v_id;

  if found then
    if v_marker.session_id = v_session_id
       and v_marker.product_id is not distinct from v_product_id
       and v_marker.x_normalized = v_x
       and v_marker.y_normalized = v_y
       and v_marker.deleted_at is null then
      return v_marker;
    end if;
    raise exception using errcode = '40001', message = 'count_marker_conflict';
  end if;

  insert into public.count_markers (
    id,
    session_id,
    product_id,
    x_normalized,
    y_normalized
  ) values (
    v_id,
    v_session_id,
    v_product_id,
    v_x,
    v_y
  )
  returning * into v_marker;

  return v_marker;
end;
$$;

revoke all on function public.save_count_session(jsonb, bigint) from public;
revoke all on function public.add_count_marker(jsonb) from public;
grant execute on function public.save_count_session(jsonb, bigint) to authenticated;
grant execute on function public.add_count_marker(jsonb) to authenticated;


-- ---------------------------------------------------------
-- MIGRATION: 202609040001_profile_active_status.sql
-- ---------------------------------------------------------
-- Add active status to profiles and enforce active authorization across application roles.

alter table public.profiles
  add column if not exists is_active boolean not null default true;

create or replace function public.current_app_role()
returns text
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select p.role
  from public.profiles as p
  where p.user_id = auth.uid()
    and p.is_active;
$$;

revoke all on function public.current_app_role() from public;
grant execute on function public.current_app_role() to authenticated;


-- ---------------------------------------------------------
-- MIGRATION: 202609060001_fix_commit_sale_idempotency.sql
-- ---------------------------------------------------------
-- Migration: 202609060001_fix_commit_sale_idempotency.sql
-- Description: Fix commit_sale RPC to guarantee point_ledger.idempotency_key is never null,
-- enforce deterministic event idempotency, support idempotent retries, and validate all inputs.

create or replace function public.commit_sale(
  p_order jsonb,
  p_idempotency_key text,
  p_device_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_order_id text := btrim(coalesce(p_order ->> 'id', ''));
  v_customer_id text := btrim(coalesce(p_order ->> 'customer_id', ''));
  v_customer_name text;
  v_held_bill_id text := nullif(btrim(coalesce(p_order ->> 'held_bill_id', '')), '');
  v_items jsonb := case when jsonb_typeof(p_order -> 'items') = 'array' then p_order -> 'items' else '[]'::jsonb end;
  v_point_uses jsonb := case when jsonb_typeof(p_order -> 'point_uses') = 'array' then p_order -> 'point_uses' else '[]'::jsonb end;
  v_free_items jsonb := case when jsonb_typeof(p_order -> 'free_items') = 'array' then p_order -> 'free_items' else '[]'::jsonb end;
  v_item jsonb;
  v_use jsonb;
  v_free jsonb;
  v_product_id bigint;
  v_qty integer;
  v_points integer;
  v_item_id text;
  v_product_name text;
  v_product_volume text;
  v_product_price numeric(12,2);
  v_total numeric(12,2) := 0;
  v_total_qty integer := 0;
  v_balance bigint;
  v_balances jsonb;
  v_seen_paid bigint[] := array[]::bigint[];
  v_seen_uses bigint[] := array[]::bigint[];
  v_seen_free bigint[] := array[]::bigint[];
  v_seen_item_ids text[] := array[]::text[];
  v_existing public.orders%rowtype;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id
    and p.is_active;

  -- Do not auto-provision or reactivate profiles from a sale RPC.
  -- An administrator must explicitly create/activate staff accounts.
  if v_role is null then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;

  if v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;

  if jsonb_typeof(p_order) is distinct from 'object' then
    raise exception using errcode = 'P0001', message = 'invalid_order_payload';
  end if;

  if btrim(coalesce(p_idempotency_key, '')) = '' then
    raise exception using errcode = 'P0001', message = 'missing_idempotency_key';
  end if;
  if v_order_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_order_id';
  end if;
  if v_customer_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_customer_id';
  end if;

  -- Advisory lock on sale idempotency key to prevent race conditions
  perform pg_advisory_xact_lock(hashtextextended('orderapp:sale:' || p_idempotency_key, 0));

  select * into v_existing
  from public.orders
  where idempotency_key = p_idempotency_key;

  if found then
    select coalesce(jsonb_object_agg(b.product_id::text, b.balance), '{}'::jsonb)
      into v_balances
    from (
      select l.product_id, sum(l.delta)::bigint as balance
      from public.point_ledger as l
      where l.customer_id = v_existing.customer_id
      group by l.product_id
    ) as b;

    return jsonb_build_object(
      'order_id', v_existing.id,
      'total', v_existing.total,
      'total_qty', v_existing.total_qty,
      'point_balances', coalesce(v_balances, '{}'::jsonb),
      'idempotent_replay', true
    );
  end if;

  if exists (select 1 from public.orders where id = v_order_id) then
    raise exception using errcode = 'P0001', message = 'order_id_conflict';
  end if;

  -- Verify customer exists or auto-heal for offline-created customers
  select c.name into v_customer_name
  from public.customers as c
  where c.id = v_customer_id
    and c.deleted_at is null;
  if not found then
    begin
      insert into public.customers (id, name, normalized_name)
      values (
        v_customer_id,
        coalesce(nullif(btrim(coalesce(p_order ->> 'customer_name', '')), ''), 'ลูกค้า ' || v_customer_id),
        lower(coalesce(nullif(btrim(coalesce(p_order ->> 'customer_name', '')), ''), 'ลูกค้า ' || v_customer_id))
      )
      on conflict (id) do update set deleted_at = null
      returning name into v_customer_name;
    exception when unique_violation then
      select c.name into v_customer_name
      from public.customers as c
      where c.id = v_customer_id;
      if not found then
        insert into public.customers (id, name, normalized_name)
        values (
          v_customer_id,
          'ลูกค้า ' || v_customer_id,
          lower('ลูกค้า ' || v_customer_id)
        )
        on conflict (id) do update set deleted_at = null
        returning name into v_customer_name;
      end if;
    end;
  end if;

  if jsonb_array_length(v_point_uses) > 0 and jsonb_array_length(v_free_items) = 0 then
    raise exception using errcode = 'P0001', message = 'redemption_requires_free_item';
  end if;
  if jsonb_array_length(v_free_items) > 0 and jsonb_array_length(v_point_uses) = 0 then
    raise exception using errcode = 'P0001', message = 'free_item_requires_redemption';
  end if;
  if jsonb_array_length(v_items) = 0 and jsonb_array_length(v_point_uses) = 0 then
    raise exception using errcode = 'P0001', message = 'empty_sale';
  end if;

  if v_held_bill_id is not null then
    perform 1
    from public.held_bills as h
    where h.id = v_held_bill_id
      and h.customer_id = v_customer_id
    for update;
    if not found then
      raise exception using errcode = 'P0001', message = 'held_bill_not_found';
    end if;
  end if;

  for v_item in select value from jsonb_array_elements(v_items)
  loop
    v_item_id := btrim(coalesce(v_item ->> 'id', ''));
    if v_item_id = '' then
      raise exception using errcode = 'P0001', message = 'missing_order_item_id';
    end if;
    if v_item_id = any(v_seen_item_ids) then
      raise exception using errcode = 'P0001', message = 'duplicate_order_item_id';
    end if;
    v_seen_item_ids := array_append(v_seen_item_ids, v_item_id);

    begin
      v_product_id := (v_item ->> 'product_id')::bigint;
      v_qty := (v_item ->> 'qty')::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception using errcode = 'P0001', message = 'invalid_paid_item';
    end;

    if v_product_id = any(v_seen_paid) then
      raise exception using errcode = 'P0001', message = 'duplicate_paid_product';
    end if;
    v_seen_paid := array_append(v_seen_paid, v_product_id);
    if v_qty <= 0 then
      raise exception using errcode = 'P0001', message = 'invalid_paid_quantity';
    end if;

    select p.name, p.volume, p.price
      into v_product_name, v_product_volume, v_product_price
    from public.products as p
    where p.id = v_product_id
      and p.is_active;
    if not found then
      raise exception using errcode = 'P0001', message = 'product_not_available';
    end if;

    v_total := v_total + (v_qty * v_product_price);
    v_total_qty := v_total_qty + v_qty;
  end loop;

  for v_use in select value from jsonb_array_elements(v_point_uses)
  loop
    begin
      v_product_id := (v_use ->> 'product_id')::bigint;
      v_points := (v_use ->> 'points')::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception using errcode = 'P0001', message = 'invalid_point_use_payload';
    end;

    if v_points <= 0 then
      raise exception using errcode = 'P0001', message = 'invalid_point_use_points';
    end if;
    if v_product_id = any(v_seen_uses) then
      raise exception using errcode = 'P0001', message = 'duplicate_point_use_product';
    end if;
    v_seen_uses := array_append(v_seen_uses, v_product_id);

    perform pg_advisory_xact_lock(
      hashtextextended('orderapp:points:' || v_customer_id || ':' || v_product_id::text, 0)
    );

    select coalesce(sum(l.delta), 0)::bigint
      into v_balance
    from public.point_ledger as l
    where l.customer_id = v_customer_id
      and l.product_id = v_product_id;

    if v_balance < v_points then
      raise exception using errcode = 'P0001', message = 'insufficient_points';
    end if;
  end loop;

  for v_free in select value from jsonb_array_elements(v_free_items)
  loop
    begin
      v_product_id := (v_free ->> 'product_id')::bigint;
      v_qty := (v_free ->> 'qty')::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception using errcode = 'P0001', message = 'invalid_free_item_payload';
    end;

    if v_qty <= 0 then
      raise exception using errcode = 'P0001', message = 'invalid_free_item_qty';
    end if;
    if v_product_id = any(v_seen_free) then
      raise exception using errcode = 'P0001', message = 'duplicate_free_item_product';
    end if;
    v_seen_free := array_append(v_seen_free, v_product_id);

    perform 1
    from public.products as p
    where p.id = v_product_id
      and p.is_active;
    if not found then
      raise exception using errcode = 'P0001', message = 'product_not_available';
    end if;

    v_total_qty := v_total_qty + v_qty;
  end loop;

  insert into public.orders (
    id,
    idempotency_key,
    customer_id,
    customer_name_snapshot,
    total_qty,
    total,
    status,
    source_device_id,
    created_by
  ) values (
    v_order_id,
    p_idempotency_key,
    v_customer_id,
    v_customer_name,
    v_total_qty,
    v_total,
    'completed',
    p_device_id,
    v_user_id
  );

  for v_item in select value from jsonb_array_elements(v_items)
  loop
    v_item_id := btrim(v_item ->> 'id');
    v_product_id := (v_item ->> 'product_id')::bigint;
    v_qty := (v_item ->> 'qty')::integer;

    select p.name, p.volume, p.price
      into v_product_name, v_product_volume, v_product_price
    from public.products as p
    where p.id = v_product_id;

    insert into public.order_items (
      id,
      order_id,
      product_id,
      name_snapshot,
      volume_snapshot,
      unit_price,
      qty,
      subtotal
    ) values (
      v_item_id,
      v_order_id,
      v_product_id,
      v_product_name,
      v_product_volume,
      v_product_price,
      v_qty,
      v_qty * v_product_price
    );

    insert into public.point_ledger (
      id,
      idempotency_key,
      customer_id,
      product_id,
      delta,
      reason,
      order_id,
      created_by
    ) values (
      v_order_id || ':ledger:sale:' || v_product_id::text,
      p_idempotency_key || ':sale:' || v_product_id::text,
      v_customer_id,
      v_product_id,
      v_qty,
      'sale',
      v_order_id,
      v_user_id
    );
  end loop;

  for v_use in select value from jsonb_array_elements(v_point_uses)
  loop
    v_product_id := (v_use ->> 'product_id')::bigint;
    v_points := (v_use ->> 'points')::integer;

    insert into public.order_point_uses (
      id,
      order_id,
      product_id,
      points
    ) values (
      v_order_id || ':points:' || v_product_id::text,
      v_order_id,
      v_product_id,
      v_points
    );

    insert into public.point_ledger (
      id,
      idempotency_key,
      customer_id,
      product_id,
      delta,
      reason,
      order_id,
      created_by
    ) values (
      v_order_id || ':ledger:redeem:' || v_product_id::text,
      p_idempotency_key || ':redeem:' || v_product_id::text,
      v_customer_id,
      v_product_id,
      -v_points,
      'redeem',
      v_order_id,
      v_user_id
    );
  end loop;

  for v_free in select value from jsonb_array_elements(v_free_items)
  loop
    v_product_id := (v_free ->> 'product_id')::bigint;
    v_qty := (v_free ->> 'qty')::integer;

    insert into public.order_free_items (
      id,
      order_id,
      product_id,
      qty
    ) values (
      v_order_id || ':free:' || v_product_id::text,
      v_order_id,
      v_product_id,
      v_qty
    );
  end loop;

  if v_held_bill_id is not null then
    delete from public.held_bills where id = v_held_bill_id;
  end if;

  select coalesce(jsonb_object_agg(b.product_id::text, b.balance), '{}'::jsonb)
    into v_balances
  from (
    select l.product_id, sum(l.delta)::bigint as balance
    from public.point_ledger as l
    where l.customer_id = v_customer_id
    group by l.product_id
  ) as b;

  return jsonb_build_object(
    'order_id', v_order_id,
    'total', v_total,
    'total_qty', v_total_qty,
    'point_balances', coalesce(v_balances, '{}'::jsonb),
    'idempotent_replay', false
  );
end;
$$;

revoke all on function public.commit_sale(jsonb, text, text) from public;
grant execute on function public.commit_sale(jsonb, text, text) to authenticated;


-- ---------------------------------------------------------
-- MIGRATION: 202609060002_fix_commit_sale_paid_total_qty.sql
-- ---------------------------------------------------------
-- Migration: 202609060002_fix_commit_sale_paid_total_qty.sql
-- Description: Fix commit_sale RPC so orders.total_qty counts paid items only.
-- Free items from redemption are tracked in order_free_items and do not increment orders.total_qty.

create or replace function public.commit_sale(
  p_order jsonb,
  p_idempotency_key text,
  p_device_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_role text;
  v_order_id text := btrim(coalesce(p_order ->> 'id', ''));
  v_customer_id text := btrim(coalesce(p_order ->> 'customer_id', ''));
  v_customer_name text;
  v_held_bill_id text := nullif(btrim(coalesce(p_order ->> 'held_bill_id', '')), '');
  v_items jsonb := case when jsonb_typeof(p_order -> 'items') = 'array' then p_order -> 'items' else '[]'::jsonb end;
  v_point_uses jsonb := case when jsonb_typeof(p_order -> 'point_uses') = 'array' then p_order -> 'point_uses' else '[]'::jsonb end;
  v_free_items jsonb := case when jsonb_typeof(p_order -> 'free_items') = 'array' then p_order -> 'free_items' else '[]'::jsonb end;
  v_item jsonb;
  v_use jsonb;
  v_free jsonb;
  v_product_id bigint;
  v_qty integer;
  v_points integer;
  v_item_id text;
  v_product_name text;
  v_product_volume text;
  v_product_price numeric(12,2);
  v_total numeric(12,2) := 0;
  v_total_qty integer := 0;
  v_balance bigint;
  v_balances jsonb;
  v_seen_paid bigint[] := array[]::bigint[];
  v_seen_uses bigint[] := array[]::bigint[];
  v_seen_free bigint[] := array[]::bigint[];
  v_seen_item_ids text[] := array[]::text[];
  v_existing public.orders%rowtype;
begin
  if v_user_id is null then
    raise exception using errcode = 'P0001', message = 'not_authenticated';
  end if;

  select p.role into v_role
  from public.profiles as p
  where p.user_id = v_user_id
    and p.is_active;

  -- Do not auto-provision or reactivate profiles from a sale RPC.
  -- An administrator must explicitly create/activate staff accounts.
  if v_role is null then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;

  if v_role not in ('admin', 'staff') then
    raise exception using errcode = 'P0001', message = 'not_authorized';
  end if;

  if jsonb_typeof(p_order) is distinct from 'object' then
    raise exception using errcode = 'P0001', message = 'invalid_order_payload';
  end if;

  if btrim(coalesce(p_idempotency_key, '')) = '' then
    raise exception using errcode = 'P0001', message = 'missing_idempotency_key';
  end if;
  if v_order_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_order_id';
  end if;
  if v_customer_id = '' then
    raise exception using errcode = 'P0001', message = 'missing_customer_id';
  end if;

  -- Advisory lock on sale idempotency key to prevent race conditions
  perform pg_advisory_xact_lock(hashtextextended('orderapp:sale:' || p_idempotency_key, 0));

  select * into v_existing
  from public.orders
  where idempotency_key = p_idempotency_key;

  if found then
    select coalesce(jsonb_object_agg(b.product_id::text, b.balance), '{}'::jsonb)
      into v_balances
    from (
      select l.product_id, sum(l.delta)::bigint as balance
      from public.point_ledger as l
      where l.customer_id = v_existing.customer_id
      group by l.product_id
    ) as b;

    return jsonb_build_object(
      'order_id', v_existing.id,
      'total', v_existing.total,
      'total_qty', v_existing.total_qty,
      'point_balances', coalesce(v_balances, '{}'::jsonb),
      'idempotent_replay', true
    );
  end if;

  if exists (select 1 from public.orders where id = v_order_id) then
    raise exception using errcode = 'P0001', message = 'order_id_conflict';
  end if;

  -- Verify customer exists or auto-heal for offline-created customers
  select c.name into v_customer_name
  from public.customers as c
  where c.id = v_customer_id
    and c.deleted_at is null;
  if not found then
    begin
      insert into public.customers (id, name, normalized_name)
      values (
        v_customer_id,
        coalesce(nullif(btrim(coalesce(p_order ->> 'customer_name', '')), ''), 'ลูกค้า ' || v_customer_id),
        lower(coalesce(nullif(btrim(coalesce(p_order ->> 'customer_name', '')), ''), 'ลูกค้า ' || v_customer_id))
      )
      on conflict (id) do update set deleted_at = null
      returning name into v_customer_name;
    exception when unique_violation then
      select c.name into v_customer_name
      from public.customers as c
      where c.id = v_customer_id;
      if not found then
        insert into public.customers (id, name, normalized_name)
        values (
          v_customer_id,
          'ลูกค้า ' || v_customer_id,
          lower('ลูกค้า ' || v_customer_id)
        )
        on conflict (id) do update set deleted_at = null
        returning name into v_customer_name;
      end if;
    end;
  end if;

  if jsonb_array_length(v_point_uses) > 0 and jsonb_array_length(v_free_items) = 0 then
    raise exception using errcode = 'P0001', message = 'redemption_requires_free_item';
  end if;
  if jsonb_array_length(v_free_items) > 0 and jsonb_array_length(v_point_uses) = 0 then
    raise exception using errcode = 'P0001', message = 'free_item_requires_redemption';
  end if;
  if jsonb_array_length(v_items) = 0 and jsonb_array_length(v_point_uses) = 0 then
    raise exception using errcode = 'P0001', message = 'empty_sale';
  end if;

  if v_held_bill_id is not null then
    perform 1
    from public.held_bills as h
    where h.id = v_held_bill_id
      and h.customer_id = v_customer_id
    for update;
    if not found then
      raise exception using errcode = 'P0001', message = 'held_bill_not_found';
    end if;
  end if;

  for v_item in select value from jsonb_array_elements(v_items)
  loop
    v_item_id := btrim(coalesce(v_item ->> 'id', ''));
    if v_item_id = '' then
      raise exception using errcode = 'P0001', message = 'missing_order_item_id';
    end if;
    if v_item_id = any(v_seen_item_ids) then
      raise exception using errcode = 'P0001', message = 'duplicate_order_item_id';
    end if;
    v_seen_item_ids := array_append(v_seen_item_ids, v_item_id);

    begin
      v_product_id := (v_item ->> 'product_id')::bigint;
      v_qty := (v_item ->> 'qty')::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception using errcode = 'P0001', message = 'invalid_paid_item';
    end;

    if v_product_id = any(v_seen_paid) then
      raise exception using errcode = 'P0001', message = 'duplicate_paid_product';
    end if;
    v_seen_paid := array_append(v_seen_paid, v_product_id);
    if v_qty <= 0 then
      raise exception using errcode = 'P0001', message = 'invalid_paid_quantity';
    end if;

    select p.name, p.volume, p.price
      into v_product_name, v_product_volume, v_product_price
    from public.products as p
    where p.id = v_product_id
      and p.is_active;
    if not found then
      raise exception using errcode = 'P0001', message = 'product_not_available';
    end if;

    v_total := v_total + (v_qty * v_product_price);
    v_total_qty := v_total_qty + v_qty;
  end loop;

  for v_use in select value from jsonb_array_elements(v_point_uses)
  loop
    begin
      v_product_id := (v_use ->> 'product_id')::bigint;
      v_points := (v_use ->> 'points')::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception using errcode = 'P0001', message = 'invalid_point_use_payload';
    end;

    if v_points <= 0 then
      raise exception using errcode = 'P0001', message = 'invalid_point_use_points';
    end if;
    if v_product_id = any(v_seen_uses) then
      raise exception using errcode = 'P0001', message = 'duplicate_point_use_product';
    end if;
    v_seen_uses := array_append(v_seen_uses, v_product_id);

    perform pg_advisory_xact_lock(
      hashtextextended('orderapp:points:' || v_customer_id || ':' || v_product_id::text, 0)
    );

    select coalesce(sum(l.delta), 0)::bigint
      into v_balance
    from public.point_ledger as l
    where l.customer_id = v_customer_id
      and l.product_id = v_product_id;

    if v_balance < v_points then
      raise exception using errcode = 'P0001', message = 'insufficient_points';
    end if;
  end loop;

  for v_free in select value from jsonb_array_elements(v_free_items)
  loop
    begin
      v_product_id := (v_free ->> 'product_id')::bigint;
      v_qty := (v_free ->> 'qty')::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception using errcode = 'P0001', message = 'invalid_free_item_payload';
    end;

    if v_qty <= 0 then
      raise exception using errcode = 'P0001', message = 'invalid_free_item_qty';
    end if;
    if v_product_id = any(v_seen_free) then
      raise exception using errcode = 'P0001', message = 'duplicate_free_item_product';
    end if;
    v_seen_free := array_append(v_seen_free, v_product_id);

    perform 1
    from public.products as p
    where p.id = v_product_id
      and p.is_active;
    if not found then
      raise exception using errcode = 'P0001', message = 'product_not_available';
    end if;

    -- Note: free items do not increment v_total_qty.
    -- orders.total_qty represents paid item quantity only.
  end loop;

  insert into public.orders (
    id,
    idempotency_key,
    customer_id,
    customer_name_snapshot,
    total_qty,
    total,
    status,
    source_device_id,
    created_by
  ) values (
    v_order_id,
    p_idempotency_key,
    v_customer_id,
    v_customer_name,
    v_total_qty,
    v_total,
    'completed',
    p_device_id,
    v_user_id
  );

  for v_item in select value from jsonb_array_elements(v_items)
  loop
    v_item_id := btrim(v_item ->> 'id');
    v_product_id := (v_item ->> 'product_id')::bigint;
    v_qty := (v_item ->> 'qty')::integer;

    select p.name, p.volume, p.price
      into v_product_name, v_product_volume, v_product_price
    from public.products as p
    where p.id = v_product_id;

    insert into public.order_items (
      id,
      order_id,
      product_id,
      name_snapshot,
      volume_snapshot,
      unit_price,
      qty,
      subtotal
    ) values (
      v_item_id,
      v_order_id,
      v_product_id,
      v_product_name,
      v_product_volume,
      v_product_price,
      v_qty,
      v_qty * v_product_price
    );

    insert into public.point_ledger (
      id,
      idempotency_key,
      customer_id,
      product_id,
      delta,
      reason,
      order_id,
      created_by
    ) values (
      v_order_id || ':ledger:sale:' || v_product_id::text,
      p_idempotency_key || ':sale:' || v_product_id::text,
      v_customer_id,
      v_product_id,
      v_qty,
      'sale',
      v_order_id,
      v_user_id
    );
  end loop;

  for v_use in select value from jsonb_array_elements(v_point_uses)
  loop
    v_product_id := (v_use ->> 'product_id')::bigint;
    v_points := (v_use ->> 'points')::integer;

    insert into public.order_point_uses (
      id,
      order_id,
      product_id,
      points
    ) values (
      v_order_id || ':points:' || v_product_id::text,
      v_order_id,
      v_product_id,
      v_points
    );

    insert into public.point_ledger (
      id,
      idempotency_key,
      customer_id,
      product_id,
      delta,
      reason,
      order_id,
      created_by
    ) values (
      v_order_id || ':ledger:redeem:' || v_product_id::text,
      p_idempotency_key || ':redeem:' || v_product_id::text,
      v_customer_id,
      v_product_id,
      -v_points,
      'redeem',
      v_order_id,
      v_user_id
    );
  end loop;

  for v_free in select value from jsonb_array_elements(v_free_items)
  loop
    v_product_id := (v_free ->> 'product_id')::bigint;
    v_qty := (v_free ->> 'qty')::integer;

    insert into public.order_free_items (
      id,
      order_id,
      product_id,
      qty
    ) values (
      v_order_id || ':free:' || v_product_id::text,
      v_order_id,
      v_product_id,
      v_qty
    );
  end loop;

  if v_held_bill_id is not null then
    delete from public.held_bills where id = v_held_bill_id;
  end if;

  select coalesce(jsonb_object_agg(b.product_id::text, b.balance), '{}'::jsonb)
    into v_balances
  from (
    select l.product_id, sum(l.delta)::bigint as balance
    from public.point_ledger as l
    where l.customer_id = v_customer_id
    group by l.product_id
  ) as b;

  return jsonb_build_object(
    'order_id', v_order_id,
    'total', v_total,
    'total_qty', v_total_qty,
    'point_balances', coalesce(v_balances, '{}'::jsonb),
    'idempotent_replay', false
  );
end;
$$;

revoke all on function public.commit_sale(jsonb, text, text) from public;
grant execute on function public.commit_sale(jsonb, text, text) to authenticated;


-- ---------------------------------------------------------
-- MIGRATION: 202609110001_line_stock_notifications.sql
-- ---------------------------------------------------------
-- Private backend state for LINE group discovery and stock notification delivery.
-- Edge Functions use the service-role client; public clients have no grants.

create table public.line_group_targets (
  group_id text primary key check (btrim(group_id) <> ''),
  is_active boolean not null default true,
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.line_stock_notification_logs (
  snapshot_id text not null references public.stock_snapshots(id) on delete cascade,
  group_id text not null references public.line_group_targets(group_id) on delete cascade,
  status text not null check (status in ('pending', 'sent', 'failed', 'no_target')),
  attempts integer not null default 0 check (attempts >= 0),
  last_error text,
  sent_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (snapshot_id, group_id)
);

alter table public.line_group_targets enable row level security;
alter table public.line_stock_notification_logs enable row level security;

revoke all on table public.line_group_targets from anon, authenticated;
revoke all on table public.line_stock_notification_logs from anon, authenticated;


-- ---------------------------------------------------------
-- MIGRATION: 202609110002_line_stock_notification_safety.sql
-- ---------------------------------------------------------
-- Make notification delivery claims atomic across concurrent Edge Function invocations.
-- A pending row is an in-flight/unknown external delivery and is never auto-reclaimed:
-- this chooses at-most-once behavior when LINE accepted a message but the status write failed.
create or replace function public.claim_line_stock_notification(
  p_snapshot_id text,
  p_group_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_snapshot_id text := btrim(coalesce(p_snapshot_id, ''));
  v_group_id text := btrim(coalesce(p_group_id, ''));
  v_log public.line_stock_notification_logs%rowtype;
  v_attempts integer;
begin
  if v_snapshot_id = '' or v_group_id = '' then
    raise exception using errcode = 'P0001', message = 'invalid_line_notification_target';
  end if;

  perform pg_advisory_xact_lock(
    hashtextextended('orderapp:line-notify:' || v_snapshot_id || ':' || v_group_id, 0)
  );

  select *
  into v_log
  from public.line_stock_notification_logs
  where snapshot_id = v_snapshot_id
    and group_id = v_group_id
  for update;

  if found and v_log.status in ('sent', 'pending') then
    return jsonb_build_object(
      'claimed', false,
      'status', v_log.status,
      'attempts', v_log.attempts
    );
  end if;

  if found then
    v_attempts := v_log.attempts + 1;
    update public.line_stock_notification_logs
    set status = 'pending',
        attempts = v_attempts,
        last_error = null,
        sent_at = null,
        updated_at = now()
    where snapshot_id = v_snapshot_id
      and group_id = v_group_id;
  else
    v_attempts := 1;
    insert into public.line_stock_notification_logs (
      snapshot_id,
      group_id,
      status,
      attempts,
      updated_at
    ) values (
      v_snapshot_id,
      v_group_id,
      'pending',
      v_attempts,
      now()
    );
  end if;

  return jsonb_build_object(
    'claimed', true,
    'status', 'pending',
    'attempts', v_attempts
  );
end;
$$;

revoke all on function public.claim_line_stock_notification(text, text)
from public, anon, authenticated;

grant execute on function public.claim_line_stock_notification(text, text)
to service_role;


-- ---------------------------------------------------------
-- SEED DATA (Products)
-- ---------------------------------------------------------
insert into public.products (id, name, tag, volume, price, is_active)
values
  (1, 'ผสมขวด 75 1500ml', 'Herbal Drink', '1500 ml', 75.00, true),
  (2, 'ผสมขวด 50 1000 ml', 'Herbal Drink', '1000 ml', 50.00, true),
  (3, 'น้ำดิป 35 1500ml', 'Herbal Drink', '1500 ml', 35.00, true),
  (4, 'ยา', 'Herbal Drink', '60 ml', 55.00, true)
on conflict (id) do update
set name = excluded.name,
    tag = excluded.tag,
    volume = excluded.volume,
    price = excluded.price,
    is_active = excluded.is_active,
    updated_at = now();


