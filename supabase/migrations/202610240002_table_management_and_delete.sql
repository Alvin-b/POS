-- Allow cashiers (waiters), managers, and owners to create dining tables in clubs,
-- and provide a safe delete_dining_table RPC that prevents deleting tables with open bills.

CREATE OR REPLACE FUNCTION public.create_dining_table(
    _shop uuid,
    _name text
) RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_caller_role text;
    v_supports_tables boolean;
    v_table_id uuid;
    v_created_at timestamptz;
BEGIN
    -- Check role: owner, manager, or cashier (waiter) can manage dining tables
    SELECT role INTO v_caller_role
    FROM public.shop_members
    WHERE shop_id = _shop AND user_id = auth.uid() AND status = 'active';

    IF v_caller_role NOT IN ('owner', 'manager', 'cashier') THEN
        RAISE EXCEPTION 'Only business staff (owner, manager, waiter) can add tables.';
    END IF;

    -- Verify the business supports tables
    SELECT coalesce((feature_flags->>'tables')::boolean, false) INTO v_supports_tables
    FROM public.shops
    WHERE id = _shop;

    IF NOT coalesce(v_supports_tables, false) THEN
        RAISE EXCEPTION 'This business type does not support table management.';
    END IF;

    IF coalesce(trim(_name), '') = '' THEN
        RAISE EXCEPTION 'Table name is required.';
    END IF;

    INSERT INTO public.dining_tables (shop_id, name, created_at)
    VALUES (_shop, trim(_name), now())
    ON CONFLICT (shop_id, name) DO NOTHING
    RETURNING id, created_at INTO v_table_id, v_created_at;

    IF v_table_id IS NULL THEN
        SELECT id, created_at INTO v_table_id, v_created_at
        FROM public.dining_tables
        WHERE shop_id = _shop AND name = trim(_name);
    END IF;

    PERFORM public.write_audit_event(
        _shop, 'dining_table_created', 'dining_table', v_table_id,
        jsonb_build_object('name', trim(_name), 'role', v_caller_role)
    );

    RETURN jsonb_build_object(
        'table_id', v_table_id,
        'id', v_table_id,
        'name', trim(_name),
        'created_at', v_created_at
    );
END;
$$;

REVOKE ALL ON FUNCTION public.create_dining_table(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_dining_table(uuid, text) TO authenticated;

-- Safe delete dining table RPC
CREATE OR REPLACE FUNCTION public.delete_dining_table(
    _shop uuid,
    _table uuid
) RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_caller_role text;
    v_open_orders_count integer;
    v_table_name text;
BEGIN
    SELECT role INTO v_caller_role
    FROM public.shop_members
    WHERE shop_id = _shop AND user_id = auth.uid() AND status = 'active';

    IF v_caller_role NOT IN ('owner', 'manager', 'cashier') THEN
        RAISE EXCEPTION 'forbidden';
    END IF;

    SELECT name INTO v_table_name
    FROM public.dining_tables
    WHERE id = _table AND shop_id = _shop;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'table_not_found';
    END IF;

    -- Guard: check if table has open unsettled orders
    SELECT count(*) INTO v_open_orders_count
    FROM public.table_orders
    WHERE shop_id = _shop AND table_id = _table AND settlement_id IS NULL;

    IF v_open_orders_count > 0 THEN
        RAISE EXCEPTION 'table_has_open_orders';
    END IF;

    -- Delete any previous settled history records if cascading, then delete table
    DELETE FROM public.dining_tables
    WHERE id = _table AND shop_id = _shop;

    PERFORM public.write_audit_event(
        _shop, 'dining_table_deleted', 'dining_table', _table,
        jsonb_build_object('name', v_table_name, 'role', v_caller_role)
    );

    RETURN jsonb_build_object(
        'success', true,
        'table_id', _table
    );
END;
$$;

REVOKE ALL ON FUNCTION public.delete_dining_table(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_dining_table(uuid, uuid) TO authenticated;

NOTIFY pgrst, 'reload schema';
