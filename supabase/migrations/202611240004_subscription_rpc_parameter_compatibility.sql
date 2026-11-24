-- 202611240004_subscription_rpc_parameter_compatibility.sql
-- PostgREST matches RPCs from the named JSON keys. Older APKs omit p_ends_at
-- and p_note, so they need an exact five-parameter compatibility overload.

ALTER TABLE public.shops
  ADD COLUMN IF NOT EXISTS subscription_ends_at timestamptz,
  ADD COLUMN IF NOT EXISTS subscription_status text NOT NULL DEFAULT 'trial',
  ADD COLUMN IF NOT EXISTS subscription_price_ksh integer NOT NULL DEFAULT 2500,
  ADD COLUMN IF NOT EXISTS subscription_plan_days integer NOT NULL DEFAULT 30,
  ADD COLUMN IF NOT EXISTS trial_ends_at timestamptz,
  ADD COLUMN IF NOT EXISTS custom_billing_note text;

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
  v_shop public.shops;
  v_status text := nullif(lower(btrim(p_status)), '');
BEGIN
  IF NOT public.is_platform_admin() THEN
    RAISE EXCEPTION 'Only platform administrators can update subscriptions.';
  END IF;
  IF p_price_ksh IS NOT NULL AND p_price_ksh <= 0 THEN
    RAISE EXCEPTION 'Subscription price must be greater than zero.';
  END IF;
  IF p_plan_days IS NOT NULL AND p_plan_days <= 0 THEN
    RAISE EXCEPTION 'Subscription plan days must be greater than zero.';
  END IF;
  IF v_status IS NOT NULL AND v_status NOT IN ('trial', 'active', 'expired') THEN
    RAISE EXCEPTION 'Invalid subscription status: %.', v_status;
  END IF;

  UPDATE public.shops
  SET subscription_price_ksh = coalesce(p_price_ksh, subscription_price_ksh),
      subscription_ends_at = coalesce(p_ends_at, subscription_ends_at),
      subscription_status = coalesce(v_status, subscription_status),
      subscription_plan_days = coalesce(p_plan_days, subscription_plan_days),
      trial_ends_at = coalesce(p_trial_ends_at, trial_ends_at),
      custom_billing_note = coalesce(p_note, custom_billing_note)
  WHERE id = p_shop_id
  RETURNING * INTO v_shop;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Business not found.';
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'shop_id', v_shop.id,
    'subscription_status', v_shop.subscription_status,
    'subscription_price_ksh', v_shop.subscription_price_ksh,
    'subscription_ends_at', v_shop.subscription_ends_at,
    'trial_ends_at', v_shop.trial_ends_at,
    'subscription_plan_days', v_shop.subscription_plan_days,
    'custom_billing_note', v_shop.custom_billing_note
  );
END;
$$;

-- Exact signature used by the current APK when it updates only price, status,
-- plan days, and trial end date.
CREATE OR REPLACE FUNCTION public.admin_update_shop_subscription(
  p_shop_id uuid,
  p_price_ksh integer,
  p_status text,
  p_plan_days integer,
  p_trial_ends_at timestamptz
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_shop public.shops;
  v_status text := nullif(lower(btrim(p_status)), '');
BEGIN
  IF NOT public.is_platform_admin() THEN
    RAISE EXCEPTION 'Only platform administrators can update subscriptions.';
  END IF;
  IF p_price_ksh IS NOT NULL AND p_price_ksh <= 0 THEN
    RAISE EXCEPTION 'Subscription price must be greater than zero.';
  END IF;
  IF p_plan_days IS NOT NULL AND p_plan_days <= 0 THEN
    RAISE EXCEPTION 'Subscription plan days must be greater than zero.';
  END IF;
  IF v_status IS NOT NULL AND v_status NOT IN ('trial', 'active', 'expired') THEN
    RAISE EXCEPTION 'Invalid subscription status: %.', v_status;
  END IF;

  UPDATE public.shops
  SET subscription_price_ksh = coalesce(p_price_ksh, subscription_price_ksh),
      subscription_status = coalesce(v_status, subscription_status),
      subscription_plan_days = coalesce(p_plan_days, subscription_plan_days),
      trial_ends_at = coalesce(p_trial_ends_at, trial_ends_at)
  WHERE id = p_shop_id
  RETURNING * INTO v_shop;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Business not found.';
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'shop_id', v_shop.id,
    'subscription_status', v_shop.subscription_status,
    'subscription_price_ksh', v_shop.subscription_price_ksh,
    'subscription_ends_at', v_shop.subscription_ends_at,
    'trial_ends_at', v_shop.trial_ends_at,
    'subscription_plan_days', v_shop.subscription_plan_days,
    'custom_billing_note', v_shop.custom_billing_note
  );
END;
$$;

REVOKE ALL ON FUNCTION public.admin_update_shop_subscription(uuid, integer, timestamptz, text, integer, timestamptz, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_update_shop_subscription(uuid, integer, timestamptz, text, integer, timestamptz, text) TO authenticated;

REVOKE ALL ON FUNCTION public.admin_update_shop_subscription(uuid, integer, text, integer, timestamptz) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_update_shop_subscription(uuid, integer, text, integer, timestamptz) TO authenticated;

NOTIFY pgrst, 'reload schema';
