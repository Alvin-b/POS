-- Adds delete_service_counter RPC for owners/managers and admin_set_business_type RPC
-- for elevating a Liquor Store ('counter_seller') to a Club ('big_club').

CREATE OR REPLACE FUNCTION public.delete_service_counter(
  _shop uuid,
  _counter uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_counter record;
  v_active_count integer;
  v_has_history boolean;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;

  IF NOT public.is_platform_admin() AND NOT EXISTS (
    SELECT 1 FROM public.shop_members m
    WHERE m.shop_id = _shop
      AND m.user_id = auth.uid()
      AND m.role IN ('owner', 'manager')
      AND coalesce(m.employment_status, 'active') <> 'terminated'
  ) THEN
    RAISE EXCEPTION 'Only an active owner or manager can delete service counters.';
  END IF;

  SELECT id, shop_id, name, active
  INTO v_counter
  FROM public.service_counters
  WHERE id = _counter AND shop_id = _shop;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Service counter not found.';
  END IF;

  SELECT count(*)::integer
  INTO v_active_count
  FROM public.service_counters
  WHERE shop_id = _shop AND active = true AND id <> _counter;

  IF v_active_count < 1 THEN
    RAISE EXCEPTION 'Keep at least 1 active service counter for this business.';
  END IF;

  SELECT (
    EXISTS (SELECT 1 FROM public.sales s WHERE s.source_counter_id = _counter)
    OR EXISTS (SELECT 1 FROM public.table_orders o WHERE o.counter_id = _counter)
  ) INTO v_has_history;

  IF v_has_history THEN
    UPDATE public.service_counters
    SET active = false
    WHERE id = _counter AND shop_id = _shop;
  ELSE
    DELETE FROM public.counter_stock WHERE counter_id = _counter AND shop_id = _shop;
    DELETE FROM public.service_counters WHERE id = _counter AND shop_id = _shop;
  END IF;

  PERFORM public.write_audit_event(
    _shop,
    'service_counter_deleted',
    'service_counter',
    _counter,
    jsonb_build_object('name', v_counter.name, 'soft_archived', v_has_history)
  );

  RETURN jsonb_build_object(
    'success', true,
    'counter_id', _counter,
    'name', v_counter.name,
    'archived', v_has_history
  );
END $$;

REVOKE ALL ON FUNCTION public.delete_service_counter(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_service_counter(uuid, uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.admin_set_business_type(
  p_shop_id uuid,
  p_business_type text DEFAULT 'big_club',
  p_counter_inventory_mode text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_raw text := lower(trim(coalesce(p_business_type, '')));
  v_normalized text;
  v_flags jsonb;
  v_shop record;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;
  IF NOT public.is_platform_admin() THEN
    RAISE EXCEPTION 'Only platform administrators can change a business type.';
  END IF;

  IF v_raw IN ('big_club', 'club') THEN
    v_normalized := 'big_club';
    v_flags := jsonb_build_object(
      'tables', true,
      'employee_management', true,
      'working_hours', true,
      'fifo_inventory', true,
      'sales_analytics', true
    );
  ELSIF v_raw IN ('counter_seller', 'liquor_store') THEN
    v_normalized := 'counter_seller';
    v_flags := jsonb_build_object(
      'tables', false,
      'employee_management', true,
      'working_hours', false,
      'fifo_inventory', true,
      'sales_analytics', false
    );
  ELSE
    RAISE EXCEPTION 'invalid_business_type';
  END IF;

  UPDATE public.shops
  SET
    business_type = v_normalized,
    feature_flags = v_flags,
    counter_inventory_mode = CASE
      WHEN p_counter_inventory_mode IN ('shared', 'per_counter') THEN p_counter_inventory_mode
      ELSE counter_inventory_mode
    END
  WHERE id = p_shop_id
  RETURNING id, name, business_type, feature_flags, counter_inventory_mode INTO v_shop;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'business_not_found';
  END IF;

  IF v_normalized = 'big_club' AND NOT EXISTS (
    SELECT 1 FROM public.service_counters WHERE shop_id = p_shop_id AND active = true
  ) THEN
    INSERT INTO public.service_counters (shop_id, name, active, created_by)
    VALUES (p_shop_id, 'Main Counter', true, auth.uid())
    ON CONFLICT (shop_id, name) DO UPDATE SET active = true;
  END IF;

  PERFORM public.write_audit_event(
    p_shop_id,
    'business_type_elevated',
    'shop',
    p_shop_id,
    jsonb_build_object(
      'business_type', v_normalized,
      'feature_flags', v_flags
    )
  );

  RETURN jsonb_build_object(
    'success', true,
    'shop_id', v_shop.id,
    'name', v_shop.name,
    'business_type', v_shop.business_type,
    'feature_flags', v_shop.feature_flags,
    'counter_inventory_mode', v_shop.counter_inventory_mode
  );
END $$;

REVOKE ALL ON FUNCTION public.admin_set_business_type(uuid, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_set_business_type(uuid, text, text) TO authenticated;

NOTIFY pgrst, 'reload schema';
