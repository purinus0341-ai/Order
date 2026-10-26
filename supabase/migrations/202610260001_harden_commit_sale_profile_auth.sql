-- Security hardening based on the live Production function definition inspected on 2026-10-26.
-- Review artifact only: do not apply to Production before staging validation.
-- Prevent commit_sale from creating/reactivating profiles and restrict RPC execution.
CREATE OR REPLACE FUNCTION public.commit_sale(p_order jsonb, p_idempotency_key text, p_device_id text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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

  -- Do not create or reactivate staff profiles from the sale RPC.
  -- Provisioning and reactivation must be an explicit administrator action.
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
$function$

REVOKE ALL ON FUNCTION public.commit_sale(jsonb, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.commit_sale(jsonb, text, text) FROM anon;
GRANT EXECUTE ON FUNCTION public.commit_sale(jsonb, text, text) TO authenticated;
