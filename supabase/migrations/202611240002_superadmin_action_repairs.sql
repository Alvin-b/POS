-- 202611240002_superadmin_action_repairs.sql
-- Repairs the superadmin actions used by the Android client when the original
-- billing/status migration was not applied to the Supabase project.
-- Prerequisite: public.shops, public.is_platform_admin(), and auth.users exist.

-- Keep this repair safe for projects that already applied part of the billing
-- schema. Existing columns and values are preserved.
ALTER TABLE public.shops
  ADD COLUMN IF NOT EXISTS subscription_ends_at timestamptz,
  ADD COLUMN IF NOT EXISTS subscription_status text NOT NULL DEFAULT 'trial',
  ADD COLUMN IF NOT EXISTS subscription_price_ksh integer NOT NULL DEFAULT 2500,
  ADD COLUMN IF NOT EXISTS subscription_plan_days integer NOT NULL DEFAULT 30,
  ADD COLUMN IF NOT EXISTS trial_ends_at timestamptz,
  ADD COLUMN IF NOT EXISTS custom_billing_note text,
  ADD COLUMN IF NOT EXISTS payment_lock_active boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS payment_lock_amount_ksh integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS payment_lock_reason text,
  ADD COLUMN IF NOT EXISTS payment_lock_started_at timestamptz,
  ADD COLUMN IF NOT EXISTS suspended_at timestamptz,
  ADD COLUMN IF NOT EXISTS closed_at timestamptz,
  ADD COLUMN IF NOT EXISTS suspension_reason text,
  ADD COLUMN IF NOT EXISTS closure_reason text;

-- The client uses this RPC for Suspend/Reopen/Close actions.
CREATE OR REPLACE FUNCTION public.admin_set_business_status(
  _shop uuid,
  _status text,
  _reason text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_status text := lower(btrim(coalesce(_status, '')));
  v_shop public.shops;
BEGIN
  IF NOT public.is_platform_admin() THEN
    RAISE EXCEPTION 'Only platform administrators can change business status.';
  END IF;

  IF v_status NOT IN ('active', 'suspended', 'closed') THEN
    RAISE EXCEPTION 'Invalid business status: %. Use active, suspended, or closed.', v_status;
  END IF;

  UPDATE public.shops
  SET status = v_status,
      suspended_at = CASE WHEN v_status = 'suspended' THEN coalesce(suspended_at, now()) ELSE NULL END,
      closed_at = CASE WHEN v_status = 'closed' THEN coalesce(closed_at, now()) ELSE NULL END,
      suspension_reason = CASE WHEN v_status = 'suspended' THEN nullif(btrim(_reason), '') ELSE NULL END,
      closure_reason = CASE WHEN v_status = 'closed' THEN nullif(btrim(_reason), '') ELSE NULL END
  WHERE id = _shop
  RETURNING * INTO v_shop;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Business not found.';
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'shop_id', v_shop.id,
    'status', v_shop.status,
    'reason', coalesce(v_shop.suspension_reason, v_shop.closure_reason)
  );
END;
$$;

-- The client uses this RPC for the instant payment lock shown in the billing
-- sheet. The lock is separate from subscription_status so it can be removed
-- without changing the subscription dates.
CREATE OR REPLACE FUNCTION public.admin_set_shop_payment_lock(
  p_shop_id uuid,
  p_locked boolean,
  p_amount_ksh integer DEFAULT NULL,
  p_reason text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_shop public.shops;
BEGIN
  IF NOT public.is_platform_admin() THEN
    RAISE EXCEPTION 'Only platform administrators can change payment locks.';
  END IF;

  IF p_locked AND coalesce(p_amount_ksh, 0) < 0 THEN
    RAISE EXCEPTION 'Payment lock amount cannot be negative.';
  END IF;

  UPDATE public.shops
  SET payment_lock_active = coalesce(p_locked, false),
      payment_lock_amount_ksh = CASE
        WHEN coalesce(p_locked, false) THEN greatest(coalesce(p_amount_ksh, payment_lock_amount_ksh, subscription_price_ksh, 0), 0)
        ELSE 0
      END,
      payment_lock_reason = CASE
        WHEN coalesce(p_locked, false) THEN nullif(btrim(p_reason), '')
        ELSE NULL
      END,
      payment_lock_started_at = CASE
        WHEN coalesce(p_locked, false) THEN coalesce(payment_lock_started_at, now())
        ELSE NULL
      END
  WHERE id = p_shop_id
  RETURNING * INTO v_shop;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Business not found.';
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'shop_id', v_shop.id,
    'payment_lock_active', coalesce(v_shop.payment_lock_active, false),
    'payment_lock_amount_ksh', coalesce(v_shop.payment_lock_amount_ksh, 0),
    'payment_lock_reason', v_shop.payment_lock_reason,
    'payment_lock_started_at', v_shop.payment_lock_started_at
  );
END;
$$;

-- Recreate this exact signature so PostgREST can resolve the Android request
-- even when the earlier subscription migration was skipped.
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

REVOKE ALL ON FUNCTION public.admin_set_business_status(uuid, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_set_business_status(uuid, text, text) TO authenticated;

REVOKE ALL ON FUNCTION public.admin_set_shop_payment_lock(uuid, boolean, integer, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_set_shop_payment_lock(uuid, boolean, integer, text) TO authenticated;

REVOKE ALL ON FUNCTION public.admin_update_shop_subscription(uuid, integer, timestamptz, text, integer, timestamptz, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_update_shop_subscription(uuid, integer, timestamptz, text, integer, timestamptz, text) TO authenticated;

NOTIFY pgrst, 'reload schema';
