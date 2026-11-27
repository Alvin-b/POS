-- Allow enrolled Butchery station devices to manage what they sell
-- (meats sold, cuts, prices, buying cost, stock, payment methods, station name)
-- while keeping module enable/disable restricted to active owners, managers, and platform admins.

CREATE OR REPLACE FUNCTION public.can_manage_butchery_catalog(_shop uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_role text;
  v_device_station text;
  v_meta_station text;
  v_module_enabled boolean := false;
BEGIN
  IF v_uid IS NULL OR _shop IS NULL THEN
    RETURN false;
  END IF;

  IF public.is_platform_admin() THEN
    RETURN true;
  END IF;

  SELECT m.role::text
  INTO v_role
  FROM public.shop_members m
  WHERE m.shop_id = _shop
    AND m.user_id = v_uid
    AND coalesce(m.employment_status, 'active') = 'active'
  LIMIT 1;

  IF v_role IS NULL THEN
    RETURN false;
  END IF;

  IF lower(v_role) IN ('owner', 'manager', 'butchery') THEN
    RETURN true;
  END IF;

  IF to_regclass('public.access_device_identities') IS NOT NULL THEN
    EXECUTE 'SELECT lower(coalesce(station, '''')) FROM public.access_device_identities WHERE shop_id = $1 AND user_id = $2 ORDER BY updated_at DESC LIMIT 1'
    INTO v_device_station
    USING _shop, v_uid;
  END IF;

  SELECT lower(
    coalesce(
      nullif(u.raw_user_meta_data->>'station', ''),
      nullif(u.raw_app_meta_data->>'station', ''),
      ''
    )
  )
  INTO v_meta_station
  FROM auth.users u
  WHERE u.id = v_uid;

  IF v_device_station = 'butchery' OR v_meta_station = 'butchery' THEN
    RETURN true;
  END IF;

  IF v_device_station = 'waiter' OR v_meta_station = 'waiter' THEN
    RETURN false;
  END IF;

  -- Fallback for passkey-enrolled Butchery station devices when external passkey functions
  -- did not write station metadata: only allow if the owner already enabled Butchery.
  IF lower(v_role) = 'cashier' THEN
    SELECT coalesce(bc.enabled, false)
    INTO v_module_enabled
    FROM public.butchery_config bc
    WHERE bc.shop_id = _shop;

    RETURN coalesce(v_module_enabled, false);
  END IF;

  RETURN false;
END $$;

REVOKE ALL ON FUNCTION public.can_manage_butchery_catalog(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.can_manage_butchery_catalog(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.upsert_butchery_config(
  _shop uuid,
  _enabled boolean,
  _butchery_name text,
  _beef_only_mode boolean,
  _default_beef_price_per_kg_ksh integer,
  _payment_methods jsonb,
  _enabled_categories jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_price integer := greatest(50, least(10000, coalesce(_default_beef_price_per_kg_ksh, 680)));
  v_name text := btrim(coalesce(_butchery_name, ''));
  v_methods jsonb := CASE
    WHEN jsonb_typeof(_payment_methods) = 'array' AND jsonb_array_length(_payment_methods) > 0 THEN _payment_methods
    ELSE '["M-Pesa","Cash Payment"]'::jsonb
  END;
  v_meats jsonb := CASE
    WHEN jsonb_typeof(_enabled_categories) = 'array' AND jsonb_array_length(_enabled_categories) > 0 THEN _enabled_categories
    WHEN coalesce(_beef_only_mode, false) THEN '["Beef"]'::jsonb
    ELSE '["Beef","Goat Meat","Whole Chicken","Matumbo","Pork Chops"]'::jsonb
  END;
  v_is_admin_or_manager boolean := false;
  v_existing_enabled boolean := false;
  v_effective_enabled boolean := false;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;

  IF NOT public.can_manage_butchery_catalog(_shop) THEN
    RAISE EXCEPTION 'Only an active owner, manager, or enrolled Butchery station can configure Butchery settings.';
  END IF;

  v_is_admin_or_manager := public.is_platform_admin() OR EXISTS (
    SELECT 1 FROM public.shop_members m
    WHERE m.shop_id = _shop
      AND m.user_id = auth.uid()
      AND m.role IN ('owner', 'manager')
      AND coalesce(m.employment_status, 'active') = 'active'
  );

  SELECT bc.enabled
  INTO v_existing_enabled
  FROM public.butchery_config bc
  WHERE bc.shop_id = _shop;

  IF NOT v_is_admin_or_manager AND NOT coalesce(v_existing_enabled, false) THEN
    RAISE EXCEPTION 'Only an active owner or manager can enable the Butchery module.';
  END IF;

  v_effective_enabled := CASE
    WHEN v_is_admin_or_manager THEN coalesce(_enabled, false)
    ELSE coalesce(v_existing_enabled, true)
  END;

  IF v_name = '' THEN
    SELECT coalesce(nullif(btrim(s.name), '') || ' Butchery', 'Main Butchery')
    INTO v_name
    FROM public.shops s
    WHERE s.id = _shop;
  END IF;

  INSERT INTO public.butchery_config (
    shop_id, enabled, butchery_name, beef_only_mode,
    default_beef_price_per_kg_ksh, payment_methods, enabled_categories, updated_at
  )
  VALUES (
    _shop, v_effective_enabled, v_name, coalesce(_beef_only_mode, false),
    v_price, v_methods, v_meats, now()
  )
  ON CONFLICT (shop_id) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    butchery_name = EXCLUDED.butchery_name,
    beef_only_mode = EXCLUDED.beef_only_mode,
    default_beef_price_per_kg_ksh = EXCLUDED.default_beef_price_per_kg_ksh,
    payment_methods = EXCLUDED.payment_methods,
    enabled_categories = EXCLUDED.enabled_categories,
    updated_at = now();

  -- Seed starter meats once if the shop has no butchery items yet
  IF NOT EXISTS (SELECT 1 FROM public.butchery_cuts WHERE shop_id = _shop) THEN
    INSERT INTO public.butchery_cuts (
      id, shop_id, name, category, price_per_kg_ksh, buying_price_per_kg_ksh, stock_grams, icon_key, active, sort_order
    )
    VALUES
      (_shop::text || '_beef', _shop, 'Beef', '', v_price, 0, 0, 'beef', true, 1),
      (_shop::text || '_goat_meat', _shop, 'Goat Meat', '', 750, 0, 0, 'goat', true, 2),
      (_shop::text || '_whole_chicken', _shop, 'Whole Chicken', '', 480, 0, 0, 'chicken', true, 3),
      (_shop::text || '_matumbo', _shop, 'Matumbo', '', 280, 0, 0, 'matumbo', true, 4),
      (_shop::text || '_pork_chops', _shop, 'Pork Chops', '', 700, 0, 0, 'pork', true, 5)
    ON CONFLICT (id) DO NOTHING;
  END IF;

  PERFORM public.write_audit_event(
    _shop,
    'butchery_config_updated',
    'butchery_config',
    _shop,
    jsonb_build_object(
      'enabled', v_effective_enabled,
      'butchery_name', v_name,
      'enabled_meats', v_meats,
      'payment_methods', v_methods
    )
  );

  RETURN jsonb_build_object('success', true, 'shop_id', _shop, 'enabled', v_effective_enabled);
END $$;

REVOKE ALL ON FUNCTION public.upsert_butchery_config(uuid, boolean, text, boolean, integer, jsonb, jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.upsert_butchery_config(uuid, boolean, text, boolean, integer, jsonb, jsonb) TO authenticated;

CREATE OR REPLACE FUNCTION public.upsert_butchery_cut(
  _shop uuid,
  _id text,
  _name text,
  _category text DEFAULT '',
  _price_per_kg_ksh integer DEFAULT 680,
  _buying_price_per_kg_ksh integer DEFAULT 0,
  _stock_grams integer DEFAULT 0,
  _icon_key text DEFAULT 'beef',
  _active boolean DEFAULT true,
  _sort_order integer DEFAULT 0
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;
  IF NOT public.can_manage_butchery_catalog(_shop) THEN
    RAISE EXCEPTION 'Only an active owner, manager, or enrolled Butchery station can edit meat items.';
  END IF;

  INSERT INTO public.butchery_cuts (
    id, shop_id, name, category, price_per_kg_ksh, buying_price_per_kg_ksh,
    stock_grams, icon_key, active, sort_order, updated_at
  )
  VALUES (
    _id,
    _shop,
    btrim(_name),
    coalesce(btrim(_category), ''),
    greatest(1, _price_per_kg_ksh),
    greatest(0, coalesce(_buying_price_per_kg_ksh, 0)),
    greatest(0, coalesce(_stock_grams, 0)),
    coalesce(nullif(btrim(_icon_key), ''), 'beef'),
    coalesce(_active, true),
    coalesce(_sort_order, 0),
    now()
  )
  ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    price_per_kg_ksh = EXCLUDED.price_per_kg_ksh,
    buying_price_per_kg_ksh = EXCLUDED.buying_price_per_kg_ksh,
    stock_grams = EXCLUDED.stock_grams,
    icon_key = EXCLUDED.icon_key,
    active = EXCLUDED.active,
    sort_order = EXCLUDED.sort_order,
    updated_at = now()
  WHERE butchery_cuts.shop_id = _shop;

  RETURN jsonb_build_object('success', true, 'id', _id);
END $$;

REVOKE ALL ON FUNCTION public.upsert_butchery_cut(uuid, text, text, text, integer, integer, integer, text, boolean, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.upsert_butchery_cut(uuid, text, text, text, integer, integer, integer, text, boolean, integer) TO authenticated;

CREATE OR REPLACE FUNCTION public.update_butchery_category_price(
  _shop uuid,
  _category text,
  _price_per_kg_ksh integer
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_updated integer;
  v_price integer := greatest(1, _price_per_kg_ksh);
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;
  IF NOT public.can_manage_butchery_catalog(_shop) THEN
    RAISE EXCEPTION 'Only an active owner, manager, or enrolled Butchery station can update category prices.';
  END IF;

  UPDATE public.butchery_cuts
  SET price_per_kg_ksh = v_price,
      updated_at = now()
  WHERE shop_id = _shop
    AND (lower(name) = lower(btrim(_category)) OR lower(category) = lower(btrim(_category)));

  GET DIAGNOSTICS v_updated = ROW_COUNT;

  IF lower(btrim(_category)) = 'beef' THEN
    UPDATE public.butchery_config
    SET default_beef_price_per_kg_ksh = v_price,
        updated_at = now()
    WHERE shop_id = _shop;
  END IF;

  RETURN coalesce(v_updated, 0);
END $$;

CREATE OR REPLACE FUNCTION public.update_butchery_meat_price(
  _shop uuid,
  _meat_name text,
  _price_per_kg_ksh integer
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_updated integer;
  v_price integer := greatest(1, _price_per_kg_ksh);
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;
  IF NOT public.can_manage_butchery_catalog(_shop) THEN
    RAISE EXCEPTION 'Only an active owner, manager, or enrolled Butchery station can update meat prices.';
  END IF;

  UPDATE public.butchery_cuts
  SET price_per_kg_ksh = v_price,
      updated_at = now()
  WHERE shop_id = _shop
    AND lower(name) = lower(btrim(_meat_name));

  GET DIAGNOSTICS v_updated = ROW_COUNT;

  IF lower(btrim(_meat_name)) = 'beef' THEN
    UPDATE public.butchery_config
    SET default_beef_price_per_kg_ksh = v_price,
        updated_at = now()
    WHERE shop_id = _shop;
  END IF;

  RETURN coalesce(v_updated, 0);
END $$;

REVOKE ALL ON FUNCTION public.update_butchery_category_price(uuid, text, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_butchery_category_price(uuid, text, integer) TO authenticated;
REVOKE ALL ON FUNCTION public.update_butchery_meat_price(uuid, text, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_butchery_meat_price(uuid, text, integer) TO authenticated;

NOTIFY pgrst, 'reload schema';
