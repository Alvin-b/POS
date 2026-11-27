-- Remove hardcoded default selling prices (680/750/480/280/700) from Butchery setup
-- so each Butchery sets its own meat prices before selling.

DO $$
DECLARE
  v_con record;
BEGIN
  FOR v_con IN
    SELECT c.conname
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE n.nspname = 'public'
      AND t.relname = 'butchery_cuts'
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) ILIKE '%price_per_kg_ksh%'
      AND pg_get_constraintdef(c.oid) NOT ILIKE '%buying_price_per_kg_ksh%'
  LOOP
    EXECUTE format('ALTER TABLE public.butchery_cuts DROP CONSTRAINT IF EXISTS %I', v_con.conname);
  END LOOP;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'butchery_cuts_price_nonnegative'
  ) THEN
    ALTER TABLE public.butchery_cuts
      ADD CONSTRAINT butchery_cuts_price_nonnegative CHECK (price_per_kg_ksh >= 0);
  END IF;
END $$;

ALTER TABLE public.butchery_config
  ALTER COLUMN default_beef_price_per_kg_ksh SET DEFAULT 0;

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
  v_price integer := greatest(0, least(10000, coalesce(_default_beef_price_per_kg_ksh, 0)));
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

  -- Seed starter meats once with unset (0) selling prices unless the owner explicitly supplied Beef rate
  IF NOT EXISTS (SELECT 1 FROM public.butchery_cuts WHERE shop_id = _shop) THEN
    INSERT INTO public.butchery_cuts (
      id, shop_id, name, category, price_per_kg_ksh, buying_price_per_kg_ksh, stock_grams, icon_key, active, sort_order
    )
    VALUES
      (_shop::text || '_beef', _shop, 'Beef', '', v_price, 0, 0, 'beef', true, 1),
      (_shop::text || '_goat_meat', _shop, 'Goat Meat', '', 0, 0, 0, 'goat', true, 2),
      (_shop::text || '_whole_chicken', _shop, 'Whole Chicken', '', 0, 0, 0, 'chicken', true, 3),
      (_shop::text || '_matumbo', _shop, 'Matumbo', '', 0, 0, 0, 'matumbo', true, 4),
      (_shop::text || '_pork_chops', _shop, 'Pork Chops', '', 0, 0, 0, 'pork', true, 5)
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
  _price_per_kg_ksh integer DEFAULT 0,
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
    greatest(0, least(10000, coalesce(_price_per_kg_ksh, 0))),
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

CREATE OR REPLACE FUNCTION public.record_butchery_sale(
  _shop uuid,
  _cut_id text,
  _weight_grams integer,
  _total_ksh integer,
  _payment_method text,
  _entry_mode text,
  _operation_id uuid,
  _attendant_name text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_existing record;
  v_cut record;
  v_deduct integer;
  v_sale_id bigint;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;
  IF NOT public.is_platform_admin() AND NOT EXISTS (
    SELECT 1 FROM public.shop_members m
    WHERE m.shop_id = _shop
      AND m.user_id = auth.uid()
      AND coalesce(m.employment_status, 'active') = 'active'
  ) THEN
    RAISE EXCEPTION 'Only active business members can record Butchery sales.';
  END IF;

  IF coalesce(_weight_grams, 0) <= 0 OR coalesce(_total_ksh, 0) <= 0 THEN
    RAISE EXCEPTION 'Weight and total amount must be greater than zero.';
  END IF;

  IF _operation_id IS NOT NULL THEN
    SELECT id INTO v_existing
    FROM public.butchery_sales
    WHERE shop_id = _shop AND operation_id = _operation_id;
    IF FOUND THEN
      RETURN jsonb_build_object('success', true, 'id', v_existing.id, 'deduplicated', true);
    END IF;
  END IF;

  SELECT id, name, category, price_per_kg_ksh, buying_price_per_kg_ksh, stock_grams
  INTO v_cut
  FROM public.butchery_cuts
  WHERE id = _cut_id AND shop_id = _shop
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Meat cut not found.';
  END IF;

  IF coalesce(v_cut.price_per_kg_ksh, 0) <= 0 THEN
    RAISE EXCEPTION 'Set a selling price per kg for this meat in Butchery Settings before recording a sale.';
  END IF;

  v_deduct := CASE
    WHEN v_cut.stock_grams > 0 THEN least(v_cut.stock_grams, _weight_grams)
    ELSE 0
  END;

  IF v_deduct > 0 THEN
    UPDATE public.butchery_cuts
    SET stock_grams = greatest(0, stock_grams - v_deduct),
        updated_at = now()
    WHERE id = _cut_id AND shop_id = _shop;
  END IF;

  INSERT INTO public.butchery_sales (
    shop_id, operation_id, cut_id, cut_name, category, price_per_kg_ksh,
    buying_price_per_kg_ksh,
    weight_grams, total_ksh, payment_method, entry_mode,
    stock_deducted_grams, attendant_user_id, attendant_name
  )
  VALUES (
    _shop,
    _operation_id,
    v_cut.id,
    v_cut.name,
    v_cut.category,
    v_cut.price_per_kg_ksh,
    greatest(0, coalesce(v_cut.buying_price_per_kg_ksh, 0)),
    _weight_grams,
    _total_ksh,
    coalesce(nullif(btrim(_payment_method), ''), 'cash'),
    coalesce(nullif(btrim(_entry_mode), ''), 'amount'),
    v_deduct,
    auth.uid(),
    nullif(btrim(coalesce(_attendant_name, '')), '')
  )
  RETURNING id INTO v_sale_id;

  RETURN jsonb_build_object('success', true, 'id', v_sale_id);
END $$;

REVOKE ALL ON FUNCTION public.record_butchery_sale(uuid, text, integer, integer, text, text, uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_butchery_sale(uuid, text, integer, integer, text, text, uuid, text) TO authenticated;

NOTIFY pgrst, 'reload schema';
