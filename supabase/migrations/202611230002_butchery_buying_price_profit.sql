-- Butchery Module v2: Remove meat category requirement, store meat names only,
-- and add optional buying_price_per_kg_ksh for conditional Profit/Loss reporting.

ALTER TABLE public.butchery_config
  ALTER COLUMN enabled_categories SET DEFAULT '["Beef","Goat Meat","Whole Chicken","Matumbo","Pork Chops"]'::jsonb;

ALTER TABLE public.butchery_cuts
  ALTER COLUMN category SET DEFAULT '';

ALTER TABLE public.butchery_cuts
  ADD COLUMN IF NOT EXISTS buying_price_per_kg_ksh integer NOT NULL DEFAULT 0
  CHECK (buying_price_per_kg_ksh >= 0);

ALTER TABLE public.butchery_sales
  ALTER COLUMN category SET DEFAULT '';

ALTER TABLE public.butchery_sales
  ADD COLUMN IF NOT EXISTS buying_price_per_kg_ksh integer NOT NULL DEFAULT 0
  CHECK (buying_price_per_kg_ksh >= 0);

-- Normalize legacy category names in butchery_config.enabled_categories to meat names
UPDATE public.butchery_config cfg
SET enabled_categories = COALESCE(
  (
    SELECT jsonb_agg(
      CASE lower(btrim(val))
        WHEN 'goat/mbuzi' THEN 'Goat Meat'
        WHEN 'goat' THEN 'Goat Meat'
        WHEN 'chicken' THEN 'Whole Chicken'
        WHEN 'liver/matumbo' THEN 'Matumbo'
        WHEN 'pork' THEN 'Pork Chops'
        ELSE btrim(val)
      END
    )
    FROM jsonb_array_elements_text(cfg.enabled_categories) AS val
    WHERE btrim(val) <> ''
  ),
  '["Beef","Goat Meat","Whole Chicken","Matumbo","Pork Chops"]'::jsonb
)
WHERE cfg.enabled_categories IS NOT NULL;

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
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;
  IF NOT public.is_platform_admin() AND NOT EXISTS (
    SELECT 1 FROM public.shop_members m
    WHERE m.shop_id = _shop
      AND m.user_id = auth.uid()
      AND m.role IN ('owner', 'manager')
      AND coalesce(m.employment_status, 'active') = 'active'
  ) THEN
    RAISE EXCEPTION 'Only an active owner or manager can configure Butchery settings.';
  END IF;

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
    _shop, coalesce(_enabled, false), v_name, coalesce(_beef_only_mode, false),
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
      'enabled', coalesce(_enabled, false),
      'butchery_name', v_name,
      'enabled_meats', v_meats,
      'payment_methods', v_methods
    )
  );

  RETURN jsonb_build_object('success', true, 'shop_id', _shop, 'enabled', coalesce(_enabled, false));
END $$;

REVOKE ALL ON FUNCTION public.upsert_butchery_config(uuid, boolean, text, boolean, integer, jsonb, jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.upsert_butchery_config(uuid, boolean, text, boolean, integer, jsonb, jsonb) TO authenticated;

-- Drop old 9-arg upsert_butchery_cut signature and replace with 10-arg version
-- supporting optional _buying_price_per_kg_ksh (0 when omitted).
DROP FUNCTION IF EXISTS public.upsert_butchery_cut(uuid, text, text, text, integer, integer, text, boolean, integer);

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
  IF NOT public.is_platform_admin() AND NOT EXISTS (
    SELECT 1 FROM public.shop_members m
    WHERE m.shop_id = _shop
      AND m.user_id = auth.uid()
      AND m.role IN ('owner', 'manager')
      AND coalesce(m.employment_status, 'active') = 'active'
  ) THEN
    RAISE EXCEPTION 'Only an active owner or manager can edit meat items.';
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
  IF NOT public.is_platform_admin() AND NOT EXISTS (
    SELECT 1 FROM public.shop_members m
    WHERE m.shop_id = _shop
      AND m.user_id = auth.uid()
      AND m.role IN ('owner', 'manager')
      AND coalesce(m.employment_status, 'active') = 'active'
  ) THEN
    RAISE EXCEPTION 'Only an active owner or manager can update meat prices.';
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

REVOKE ALL ON FUNCTION public.update_butchery_meat_price(uuid, text, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_butchery_meat_price(uuid, text, integer) TO authenticated;

-- Snapshot buying_price_per_kg_ksh into butchery_sales so historical profit/loss stays accurate
-- even if wholesale buying prices change later (remains 0 when buying price was omitted).
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
    RAISE EXCEPTION 'Meat item not found.';
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
    buying_price_per_kg_ksh, weight_grams, total_ksh, payment_method, entry_mode,
    stock_deducted_grams, attendant_user_id, attendant_name
  )
  VALUES (
    _shop,
    _operation_id,
    v_cut.id,
    v_cut.name,
    coalesce(v_cut.category, ''),
    v_cut.price_per_kg_ksh,
    coalesce(v_cut.buying_price_per_kg_ksh, 0),
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
