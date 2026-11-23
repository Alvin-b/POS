-- Butchery cost accounting and offline-safe synchronization.
-- This migration is idempotent and can be applied with `supabase db push` or
-- pasted into the Supabase SQL editor.

CREATE TABLE IF NOT EXISTS public.butchery_config (
  shop_id uuid PRIMARY KEY,
  enabled boolean NOT NULL DEFAULT false,
  butchery_name text NOT NULL DEFAULT '',
  beef_only_mode boolean NOT NULL DEFAULT false,
  default_beef_price_per_kg_ksh integer NOT NULL DEFAULT 0 CHECK (default_beef_price_per_kg_ksh >= 0),
  payment_methods jsonb NOT NULL DEFAULT '["M-Pesa", "Cash Payment"]'::jsonb,
  enabled_categories jsonb NOT NULL DEFAULT '["Beef", "Goat Meat", "Whole Chicken", "Matumbo", "Pork Chops"]'::jsonb,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.butchery_cuts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  shop_id uuid NOT NULL,
  name text NOT NULL,
  category text NOT NULL DEFAULT '',
  price_per_kg_ksh integer NOT NULL DEFAULT 0 CHECK (price_per_kg_ksh >= 0),
  buying_price_per_kg_ksh integer NOT NULL DEFAULT 0 CHECK (buying_price_per_kg_ksh >= 0),
  stock_grams integer NOT NULL DEFAULT 0 CHECK (stock_grams >= 0),
  icon_key text NOT NULL DEFAULT 'beef',
  active boolean NOT NULL DEFAULT true,
  sort_order integer NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (shop_id, name)
);

CREATE TABLE IF NOT EXISTS public.butchery_sales (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  shop_id uuid NOT NULL,
  cut_id uuid NOT NULL REFERENCES public.butchery_cuts(id),
  cut_name text NOT NULL,
  category text NOT NULL DEFAULT '',
  price_per_kg_ksh integer NOT NULL DEFAULT 0 CHECK (price_per_kg_ksh >= 0),
  buying_price_per_kg_ksh integer NOT NULL DEFAULT 0 CHECK (buying_price_per_kg_ksh >= 0),
  weight_grams integer NOT NULL CHECK (weight_grams > 0),
  total_ksh integer NOT NULL CHECK (total_ksh > 0),
  payment_method text NOT NULL DEFAULT 'cash',
  entry_mode text NOT NULL DEFAULT 'weight',
  stock_deducted_grams integer NOT NULL DEFAULT 0 CHECK (stock_deducted_grams >= 0),
  attendant_name text,
  operation_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  undone boolean NOT NULL DEFAULT false,
  UNIQUE (shop_id, operation_id)
);

ALTER TABLE public.butchery_config
  ADD COLUMN IF NOT EXISTS payment_methods jsonb NOT NULL DEFAULT '["M-Pesa", "Cash Payment"]'::jsonb,
  ADD COLUMN IF NOT EXISTS enabled_categories jsonb NOT NULL DEFAULT '["Beef", "Goat Meat", "Whole Chicken", "Matumbo", "Pork Chops"]'::jsonb;

ALTER TABLE public.butchery_cuts
  ADD COLUMN IF NOT EXISTS category text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS buying_price_per_kg_ksh integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS stock_grams integer NOT NULL DEFAULT 0;

ALTER TABLE public.butchery_sales
  ADD COLUMN IF NOT EXISTS category text NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS buying_price_per_kg_ksh integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS operation_id uuid,
  ADD COLUMN IF NOT EXISTS stock_deducted_grams integer NOT NULL DEFAULT 0;

ALTER TABLE public.butchery_cuts
  DROP CONSTRAINT IF EXISTS butchery_cuts_buying_price_per_kg_ksh_check,
  ADD CONSTRAINT butchery_cuts_buying_price_per_kg_ksh_check CHECK (buying_price_per_kg_ksh >= 0);
ALTER TABLE public.butchery_sales
  DROP CONSTRAINT IF EXISTS butchery_sales_buying_price_per_kg_ksh_check,
  ADD CONSTRAINT butchery_sales_buying_price_per_kg_ksh_check CHECK (buying_price_per_kg_ksh >= 0);
CREATE UNIQUE INDEX IF NOT EXISTS butchery_sales_shop_operation_id_uidx
  ON public.butchery_sales(shop_id, operation_id)
  WHERE operation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS butchery_cuts_shop_active_idx
  ON public.butchery_cuts(shop_id, active, sort_order, name);
CREATE INDEX IF NOT EXISTS butchery_sales_shop_created_idx
  ON public.butchery_sales(shop_id, created_at DESC);

UPDATE public.butchery_config
SET enabled_categories = (
  SELECT coalesce(jsonb_agg(mapped ORDER BY ord), '[]'::jsonb)
  FROM (
    SELECT DISTINCT ON (lower(value))
      CASE lower(trim(value))
        WHEN 'goat' THEN 'Goat Meat'
        WHEN 'mbuzi' THEN 'Goat Meat'
        WHEN 'goat/mbuzi' THEN 'Goat Meat'
        WHEN 'chicken' THEN 'Whole Chicken'
        WHEN 'whole chicken' THEN 'Whole Chicken'
        WHEN 'liver' THEN 'Matumbo'
        WHEN 'matumbo' THEN 'Matumbo'
        WHEN 'liver/matumbo' THEN 'Matumbo'
        WHEN 'pork' THEN 'Pork Chops'
        WHEN 'pork chops' THEN 'Pork Chops'
        WHEN 'beef' THEN 'Beef'
        ELSE trim(value)
      END AS mapped,
      ord
    FROM jsonb_array_elements_text(coalesce(enabled_categories, '[]'::jsonb)) WITH ORDINALITY AS x(value, ord)
  ) normalized
  WHERE mapped IN ('Beef', 'Goat Meat', 'Whole Chicken', 'Matumbo', 'Pork Chops')
)
WHERE enabled_categories IS NOT NULL;

UPDATE public.butchery_cuts
SET category = CASE lower(trim(category))
  WHEN 'goat' THEN 'Goat Meat'
  WHEN 'mbuzi' THEN 'Goat Meat'
  WHEN 'goat/mbuzi' THEN 'Goat Meat'
  WHEN 'chicken' THEN 'Whole Chicken'
  WHEN 'whole chicken' THEN 'Whole Chicken'
  WHEN 'liver' THEN 'Matumbo'
  WHEN 'matumbo' THEN 'Matumbo'
  WHEN 'liver/matumbo' THEN 'Matumbo'
  WHEN 'pork' THEN 'Pork Chops'
  WHEN 'pork chops' THEN 'Pork Chops'
  ELSE category
END;

UPDATE public.butchery_sales
SET category = CASE lower(trim(category))
  WHEN 'goat' THEN 'Goat Meat'
  WHEN 'mbuzi' THEN 'Goat Meat'
  WHEN 'goat/mbuzi' THEN 'Goat Meat'
  WHEN 'chicken' THEN 'Whole Chicken'
  WHEN 'whole chicken' THEN 'Whole Chicken'
  WHEN 'liver' THEN 'Matumbo'
  WHEN 'matumbo' THEN 'Matumbo'
  WHEN 'liver/matumbo' THEN 'Matumbo'
  WHEN 'pork' THEN 'Pork Chops'
  WHEN 'pork chops' THEN 'Pork Chops'
  ELSE category
END;

CREATE OR REPLACE FUNCTION public.upsert_butchery_config(
  _shop uuid,
  _enabled boolean DEFAULT true,
  _butchery_name text DEFAULT '',
  _beef_only_mode boolean DEFAULT false,
  _default_beef_price_per_kg_ksh integer DEFAULT 0,
  _payment_methods jsonb DEFAULT '["M-Pesa", "Cash Payment"]'::jsonb,
  _enabled_categories jsonb DEFAULT '["Beef", "Goat Meat", "Whole Chicken", "Matumbo", "Pork Chops"]'::jsonb
)
RETURNS public.butchery_config
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE
  result public.butchery_config;
  categories jsonb := CASE WHEN _beef_only_mode THEN '["Beef"]'::jsonb ELSE coalesce(_enabled_categories, '[]'::jsonb) END;
BEGIN
  IF NOT public.has_shop_role(_shop, ARRAY['owner', 'manager']::public.shop_role[]) THEN
    RAISE EXCEPTION 'forbidden';
  END IF;
  IF jsonb_array_length(categories) = 0 THEN
    categories := '["Beef", "Goat Meat", "Whole Chicken", "Matumbo", "Pork Chops"]'::jsonb;
  END IF;
  INSERT INTO public.butchery_config(
    shop_id, enabled, butchery_name, beef_only_mode,
    default_beef_price_per_kg_ksh, payment_methods, enabled_categories, updated_at
  ) VALUES (
    _shop, coalesce(_enabled, true), left(trim(coalesce(_butchery_name, '')), 120),
    coalesce(_beef_only_mode, false), greatest(coalesce(_default_beef_price_per_kg_ksh, 0), 0),
    coalesce(_payment_methods, '["M-Pesa", "Cash Payment"]'::jsonb), categories, now()
  ) ON CONFLICT (shop_id) DO UPDATE SET
    enabled = excluded.enabled,
    butchery_name = excluded.butchery_name,
    beef_only_mode = excluded.beef_only_mode,
    default_beef_price_per_kg_ksh = excluded.default_beef_price_per_kg_ksh,
    payment_methods = excluded.payment_methods,
    enabled_categories = excluded.enabled_categories,
    updated_at = now()
  RETURNING * INTO result;

  INSERT INTO public.butchery_cuts(shop_id, name, category, price_per_kg_ksh, buying_price_per_kg_ksh, stock_grams, icon_key, sort_order)
  SELECT _shop, starter.name, starter.category, starter.price, 0, 0, starter.icon_key, starter.sort_order
  FROM (VALUES
    ('Beef', 'Beef', greatest(coalesce(_default_beef_price_per_kg_ksh, 0), 0), 'beef', 1),
    ('Goat Meat', 'Goat Meat', 0, 'goat', 2),
    ('Whole Chicken', 'Whole Chicken', 0, 'chicken', 3),
    ('Matumbo', 'Matumbo', 0, 'matumbo', 4),
    ('Pork Chops', 'Pork Chops', 0, 'pork', 5)
  ) AS starter(name, category, price, icon_key, sort_order)
  WHERE categories ? starter.category
  ON CONFLICT (shop_id, name) DO NOTHING;
  RETURN result;
END $$;

CREATE OR REPLACE FUNCTION public.upsert_butchery_cut(
  _shop uuid,
  _cut_id uuid DEFAULT NULL,
  _name text DEFAULT '',
  _category text DEFAULT '',
  _price_per_kg_ksh integer DEFAULT 0,
  _buying_price_per_kg_ksh integer DEFAULT 0,
  _stock_grams integer DEFAULT 0,
  _icon_key text DEFAULT 'beef',
  _active boolean DEFAULT true,
  _sort_order integer DEFAULT 0
)
RETURNS public.butchery_cuts
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE result public.butchery_cuts;
BEGIN
  IF NOT public.has_shop_role(_shop, ARRAY['owner', 'manager']::public.shop_role[]) THEN RAISE EXCEPTION 'forbidden'; END IF;
  IF length(trim(coalesce(_name, ''))) NOT BETWEEN 1 AND 80 THEN RAISE EXCEPTION 'invalid_meat_name'; END IF;
  IF _price_per_kg_ksh < 0 OR _buying_price_per_kg_ksh < 0 OR _stock_grams < 0 THEN RAISE EXCEPTION 'invalid_butchery_values'; END IF;
  INSERT INTO public.butchery_cuts(id, shop_id, name, category, price_per_kg_ksh, buying_price_per_kg_ksh, stock_grams, icon_key, active, sort_order, updated_at)
  VALUES (coalesce(_cut_id, gen_random_uuid()), _shop, trim(_name), trim(coalesce(_category, '')), _price_per_kg_ksh, _buying_price_per_kg_ksh, _stock_grams, coalesce(nullif(trim(_icon_key), ''), 'beef'), coalesce(_active, true), greatest(_sort_order, 0), now())
  ON CONFLICT (id) DO UPDATE SET
    name = excluded.name, category = excluded.category, price_per_kg_ksh = excluded.price_per_kg_ksh,
    buying_price_per_kg_ksh = excluded.buying_price_per_kg_ksh, stock_grams = excluded.stock_grams,
    icon_key = excluded.icon_key, active = excluded.active, sort_order = excluded.sort_order, updated_at = now()
  RETURNING * INTO result;
  RETURN result;
END $$;

CREATE OR REPLACE FUNCTION public.update_butchery_meat_price(
  _shop uuid,
  _meat_name text,
  _price_per_kg_ksh integer
)
RETURNS SETOF public.butchery_cuts
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
  IF NOT public.has_shop_role(_shop, ARRAY['owner', 'manager']::public.shop_role[]) THEN RAISE EXCEPTION 'forbidden'; END IF;
  IF _price_per_kg_ksh < 0 OR length(trim(coalesce(_meat_name, ''))) = 0 THEN RAISE EXCEPTION 'invalid_butchery_price'; END IF;
  IF lower(trim(_meat_name)) = 'beef' THEN
    UPDATE public.butchery_config SET default_beef_price_per_kg_ksh = _price_per_kg_ksh, updated_at = now() WHERE shop_id = _shop;
  END IF;
  RETURN QUERY UPDATE public.butchery_cuts
    SET price_per_kg_ksh = _price_per_kg_ksh, updated_at = now()
    WHERE shop_id = _shop AND lower(name) = lower(trim(_meat_name))
    RETURNING *;
END $$;

CREATE OR REPLACE FUNCTION public.record_butchery_sale(
  _shop uuid,
  _cut_id uuid,
  _weight_grams integer,
  _total_ksh integer,
  _payment_method text DEFAULT 'cash',
  _entry_mode text DEFAULT 'weight',
  _operation_id uuid DEFAULT NULL,
  _attendant_name text DEFAULT NULL
)
RETURNS public.butchery_sales
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE
  cut_row public.butchery_cuts;
  existing_sale public.butchery_sales;
  result public.butchery_sales;
  deducted integer := 0;
BEGIN
  IF NOT public.has_shop_role(_shop, ARRAY['owner', 'manager', 'cashier']::public.shop_role[]) THEN RAISE EXCEPTION 'forbidden'; END IF;
  IF _weight_grams <= 0 OR _total_ksh <= 0 THEN RAISE EXCEPTION 'invalid_butchery_sale'; END IF;
  IF _operation_id IS NOT NULL THEN
    SELECT * INTO existing_sale FROM public.butchery_sales WHERE shop_id = _shop AND operation_id = _operation_id;
    IF FOUND THEN RETURN existing_sale; END IF;
  END IF;
  SELECT * INTO cut_row FROM public.butchery_cuts WHERE id = _cut_id AND shop_id = _shop AND active FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'butchery_cut_not_found'; END IF;
  IF cut_row.stock_grams > 0 THEN
    IF cut_row.stock_grams < _weight_grams THEN RAISE EXCEPTION 'insufficient_butchery_stock'; END IF;
    deducted := _weight_grams;
    UPDATE public.butchery_cuts SET stock_grams = stock_grams - deducted, updated_at = now() WHERE id = cut_row.id;
  END IF;
  INSERT INTO public.butchery_sales(
    shop_id, cut_id, cut_name, category, price_per_kg_ksh, buying_price_per_kg_ksh,
    weight_grams, total_ksh, payment_method, entry_mode, stock_deducted_grams,
    attendant_name, operation_id
  ) VALUES (
    _shop, cut_row.id, cut_row.name, coalesce(cut_row.category, ''), cut_row.price_per_kg_ksh,
    cut_row.buying_price_per_kg_ksh, _weight_grams, _total_ksh, coalesce(nullif(trim(_payment_method), ''), 'cash'),
    coalesce(nullif(trim(_entry_mode), ''), 'weight'), deducted, _attendant_name, _operation_id
  ) RETURNING * INTO result;
  RETURN result;
EXCEPTION WHEN unique_violation THEN
  SELECT * INTO result FROM public.butchery_sales WHERE shop_id = _shop AND operation_id = _operation_id;
  IF result.id IS NOT NULL THEN RETURN result; END IF;
  RAISE;
END $$;

CREATE OR REPLACE VIEW public.butchery_profit_loss AS
SELECT
  shop_id,
  date_trunc('day', created_at)::date AS sale_date,
  count(*)::bigint AS sale_count,
  coalesce(sum(total_ksh), 0)::bigint AS revenue_ksh,
  coalesce(sum(round((weight_grams / 1000.0) * buying_price_per_kg_ksh)), 0)::bigint AS cost_ksh,
  coalesce(sum(total_ksh - round((weight_grams / 1000.0) * buying_price_per_kg_ksh)), 0)::bigint AS net_profit_ksh
FROM public.butchery_sales
WHERE buying_price_per_kg_ksh > 0 AND undone = false
GROUP BY shop_id, date_trunc('day', created_at)::date;

ALTER TABLE public.butchery_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.butchery_cuts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.butchery_sales ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON FUNCTION public.upsert_butchery_config(uuid, boolean, text, boolean, integer, jsonb, jsonb) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.upsert_butchery_cut(uuid, uuid, text, text, integer, integer, integer, text, boolean, integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.update_butchery_meat_price(uuid, text, integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.record_butchery_sale(uuid, uuid, integer, integer, text, text, uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.upsert_butchery_config(uuid, boolean, text, boolean, integer, jsonb, jsonb) TO authenticated;
GRANT EXECUTE ON FUNCTION public.upsert_butchery_cut(uuid, uuid, text, text, integer, integer, integer, text, boolean, integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_butchery_meat_price(uuid, text, integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.record_butchery_sale(uuid, uuid, integer, integer, text, text, uuid, text) TO authenticated;

NOTIFY pgrst, 'reload schema';
