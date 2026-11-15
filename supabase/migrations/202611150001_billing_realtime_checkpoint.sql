-- Billing/realtime checkpoint.
-- Adds shops realtime notifications, member-readable shops RLS, and platform
-- billing defaults for newly-created shops only.

-- 1. Connected clients need shop subscription/lock changes over Realtime.
DO $$
BEGIN
  IF to_regclass('public.shops') IS NOT NULL
     AND NOT EXISTS (
       SELECT 1
       FROM pg_publication_rel pr
       JOIN pg_publication p ON p.oid = pr.prpubid
       JOIN pg_class c ON c.oid = pr.prrelid
       JOIN pg_namespace n ON n.oid = c.relnamespace
       WHERE p.pubname = 'supabase_realtime'
         AND n.nspname = 'public'
         AND c.relname = 'shops'
     ) THEN
    EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.shops';
  END IF;
END $$;

-- 2. Realtime SELECT delivery is still controlled by RLS. Members may read
-- their own shop row; platform admins may read all shop rows.
ALTER TABLE public.shops ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename = 'shops'
      AND policyname = 'members_read_their_shops'
  ) THEN
    CREATE POLICY members_read_their_shops
      ON public.shops
      FOR SELECT
      TO authenticated
      USING (
        public.is_shop_member(id)
        OR public.is_platform_admin()
      );
  END IF;
END $$;

-- 3. Store platform defaults separately. Updating this row must not update
-- existing shops or change their trial_ends_at.
CREATE TABLE IF NOT EXISTS public.platform_billing_settings (
  id boolean PRIMARY KEY DEFAULT true CHECK (id),
  trial_duration_days integer NOT NULL DEFAULT 14 CHECK (trial_duration_days > 0),
  subscription_price_ksh integer NOT NULL DEFAULT 2500 CHECK (subscription_price_ksh > 0),
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid
);

INSERT INTO public.platform_billing_settings (id)
VALUES (true)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.platform_billing_settings ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.platform_billing_settings FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.admin_get_platform_billing_settings()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_settings public.platform_billing_settings;
BEGIN
  IF NOT public.is_platform_admin() THEN
    RAISE EXCEPTION 'Only platform administrators can view billing settings.';
  END IF;

  SELECT * INTO v_settings
  FROM public.platform_billing_settings
  WHERE id = true;

  RETURN jsonb_build_object(
    'trial_duration_days', v_settings.trial_duration_days,
    'subscription_price_ksh', v_settings.subscription_price_ksh,
    'updated_at', v_settings.updated_at
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_update_platform_billing_settings(
  p_trial_duration_days integer,
  p_subscription_price_ksh integer
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_settings public.platform_billing_settings;
BEGIN
  IF NOT public.is_platform_admin() THEN
    RAISE EXCEPTION 'Only platform administrators can update billing settings.';
  END IF;
  IF p_trial_duration_days IS NULL OR p_trial_duration_days <= 0 THEN
    RAISE EXCEPTION 'Trial duration must be greater than zero.';
  END IF;
  IF p_subscription_price_ksh IS NULL OR p_subscription_price_ksh <= 0 THEN
    RAISE EXCEPTION 'Subscription price must be greater than zero.';
  END IF;

  INSERT INTO public.platform_billing_settings (
    id, trial_duration_days, subscription_price_ksh, updated_at, updated_by
  ) VALUES (
    true, p_trial_duration_days, p_subscription_price_ksh, now(), auth.uid()
  )
  ON CONFLICT (id) DO UPDATE SET
    trial_duration_days = excluded.trial_duration_days,
    subscription_price_ksh = excluded.subscription_price_ksh,
    updated_at = excluded.updated_at,
    updated_by = excluded.updated_by
  RETURNING * INTO v_settings;

  RETURN jsonb_build_object(
    'trial_duration_days', v_settings.trial_duration_days,
    'subscription_price_ksh', v_settings.subscription_price_ksh,
    'updated_at', v_settings.updated_at
  );
END;
$$;

REVOKE ALL ON FUNCTION public.admin_get_platform_billing_settings() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.admin_get_platform_billing_settings() TO authenticated;
REVOKE ALL ON FUNCTION public.admin_update_platform_billing_settings(integer, integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.admin_update_platform_billing_settings(integer, integer) TO authenticated;

-- Apply the current platform defaults only to rows being inserted. Existing
-- shops, including their trial_ends_at, are never modified by this trigger.
CREATE OR REPLACE FUNCTION public.apply_platform_billing_defaults_to_new_shop()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_settings public.platform_billing_settings;
BEGIN
  SELECT * INTO v_settings
  FROM public.platform_billing_settings
  WHERE id = true;

  IF FOUND THEN
    NEW.trial_ends_at := now() + make_interval(days => v_settings.trial_duration_days);
    NEW.subscription_price_ksh := v_settings.subscription_price_ksh;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS apply_platform_billing_defaults_to_new_shop_trigger ON public.shops;
CREATE TRIGGER apply_platform_billing_defaults_to_new_shop_trigger
BEFORE INSERT ON public.shops
FOR EACH ROW
EXECUTE FUNCTION public.apply_platform_billing_defaults_to_new_shop();

REVOKE ALL ON FUNCTION public.apply_platform_billing_defaults_to_new_shop() FROM PUBLIC, anon, authenticated;

-- 4. Keep the subscription update strictly shop-scoped and preserve the
-- existing trial when no replacement date is supplied.
CREATE OR REPLACE FUNCTION public.admin_update_shop_subscription(
    p_shop_id uuid,
    p_price_ksh integer DEFAULT NULL,
    p_ends_at timestamptz DEFAULT NULL,
    p_status text DEFAULT NULL,
    p_plan_days integer DEFAULT NULL,
    p_trial_ends_at timestamptz DEFAULT NULL,
    p_note text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_shop record;
BEGIN
    IF NOT public.is_platform_admin() THEN
        RAISE EXCEPTION 'Only platform administrators can update subscriptions.';
    END IF;

    UPDATE public.shops
    SET subscription_price_ksh = coalesce(p_price_ksh, subscription_price_ksh),
        subscription_ends_at = coalesce(p_ends_at, subscription_ends_at),
        subscription_status = coalesce(p_status, subscription_status),
        subscription_plan_days = coalesce(p_plan_days, subscription_plan_days),
        trial_ends_at = coalesce(p_trial_ends_at, trial_ends_at),
        custom_billing_note = coalesce(p_note, custom_billing_note)
    WHERE id = p_shop_id
    RETURNING * INTO v_shop;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Shop not found';
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'shop_id', v_shop.id,
        'subscription_status', v_shop.subscription_status,
        'subscription_price_ksh', v_shop.subscription_price_ksh,
        'subscription_ends_at', v_shop.subscription_ends_at,
        'trial_ends_at', v_shop.trial_ends_at,
        'subscription_plan_days', v_shop.subscription_plan_days
    );
END;
$$;

REVOKE ALL ON FUNCTION public.admin_update_shop_subscription(uuid, integer, timestamptz, text, integer, timestamptz, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.admin_update_shop_subscription(uuid, integer, timestamptz, text, integer, timestamptz, text) TO authenticated;

NOTIFY pgrst, 'reload schema';
