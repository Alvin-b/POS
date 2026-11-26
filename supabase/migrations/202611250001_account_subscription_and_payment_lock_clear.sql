-- 202611250001_account_subscription_and_payment_lock_clear.sql
-- 1. Restore subscription and payment-lock fields in public.my_account() while keeping owner_phone.
-- 2. Make public.confirm_subscription_payment(...) idempotent on duplicate callbacks and clear
--    administrator payment locks when a subscription payment succeeds.

CREATE OR REPLACE FUNCTION public.my_account()
RETURNS json
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  result json;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;

  SELECT json_build_object(
    'user_id', auth.uid(),
    'email', (SELECT u.email FROM auth.users u WHERE u.id = auth.uid()),
    'is_platform_admin', public.is_platform_admin(),
    'memberships', coalesce((
      SELECT json_agg(json_build_object(
        'shop_id', m.shop_id,
        'shop_name', s.name,
        'role', m.role,
        'status', s.status,
        'employment_status', m.employment_status,
        'business_type', s.business_type,
        'business_size', s.business_size,
        'feature_flags', s.feature_flags,
        'currency', s.currency,
        'low_stock_threshold', s.low_stock_threshold,
        'counter_inventory_mode', s.counter_inventory_mode,
        'owner_phone', CASE WHEN m.role = 'owner' THEN s.owner_phone ELSE NULL END,
        'subscription_status', CASE
          WHEN coalesce(s.payment_lock_active, false) THEN 'payment_locked'
          ELSE coalesce(s.subscription_status, 'trial')
        END,
        'trial_ends_at', s.trial_ends_at,
        'subscription_ends_at', s.subscription_ends_at,
        'subscription_price_ksh', coalesce(s.subscription_price_ksh, 2500),
        'subscription_plan_days', coalesce(s.subscription_plan_days, 30),
        'last_payment_at', s.last_payment_at,
        'last_payment_receipt', s.last_payment_receipt,
        'last_payment_phone', s.last_payment_phone,
        'payment_lock_active', coalesce(s.payment_lock_active, false),
        'payment_lock_amount_ksh', coalesce(s.payment_lock_amount_ksh, 0),
        'payment_lock_reason', s.payment_lock_reason,
        'payment_lock_started_at', s.payment_lock_started_at,
        'counter_count', (
          SELECT count(*)::int FROM public.service_counters c
          WHERE c.shop_id = s.id AND c.active
        )
      ) ORDER BY s.created_at DESC)
      FROM public.shop_members m
      JOIN public.shops s ON s.id = m.shop_id
      WHERE m.user_id = auth.uid()
    ), '[]'::json)
  ) INTO result;
  RETURN result;
END;
$$;

REVOKE ALL ON FUNCTION public.my_account() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.my_account() TO authenticated;

CREATE OR REPLACE FUNCTION public.confirm_subscription_payment(
  p_checkout_request_id text,
  p_mpesa_receipt text,
  p_status text DEFAULT 'completed',
  p_result_code integer DEFAULT 0,
  p_result_desc text DEFAULT 'Success'
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
  v_payment public.subscription_payments;
  v_shop public.shops;
  v_new_ends_at timestamptz;
  v_days integer;
  v_normalized_status text := lower(btrim(coalesce(p_status, 'completed')));
BEGIN
  SELECT * INTO v_payment
  FROM public.subscription_payments
  WHERE checkout_request_id = p_checkout_request_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RETURN jsonb_build_object('success', false, 'error', 'Payment request not found');
  END IF;

  -- Idempotency: ignore duplicate Safaricom callbacks once already marked completed
  IF v_payment.status = 'completed' AND v_normalized_status = 'completed' THEN
    SELECT * INTO v_shop FROM public.shops WHERE id = v_payment.shop_id;
    IF coalesce(v_shop.payment_lock_active, false) THEN
      UPDATE public.shops
      SET
        payment_lock_active = false,
        payment_lock_amount_ksh = 0,
        payment_lock_reason = NULL,
        payment_lock_started_at = NULL
      WHERE id = v_payment.shop_id;
    END IF;
    RETURN jsonb_build_object(
      'success', true,
      'already_confirmed', true,
      'status', 'completed',
      'mpesa_receipt', v_payment.mpesa_receipt,
      'subscription_ends_at', v_shop.subscription_ends_at
    );
  END IF;

  UPDATE public.subscription_payments
  SET
    status = v_normalized_status,
    mpesa_receipt = coalesce(nullif(btrim(p_mpesa_receipt), ''), mpesa_receipt),
    result_code = p_result_code,
    result_desc = p_result_desc,
    updated_at = now()
  WHERE id = v_payment.id
  RETURNING * INTO v_payment;

  IF v_normalized_status = 'completed' THEN
    SELECT * INTO v_shop FROM public.shops WHERE id = v_payment.shop_id FOR UPDATE;
    v_days := coalesce(nullif(v_payment.plan_days, 0), nullif(v_shop.subscription_plan_days, 0), 30);

    v_new_ends_at := coalesce(
      greatest(v_shop.subscription_ends_at, now()),
      now()
    ) + (v_days || ' days')::interval;

    UPDATE public.shops
    SET
      subscription_status = 'active',
      subscription_ends_at = v_new_ends_at,
      last_payment_at = now(),
      last_payment_receipt = coalesce(nullif(btrim(p_mpesa_receipt), ''), v_shop.last_payment_receipt),
      last_payment_phone = coalesce(v_payment.phone, v_shop.last_payment_phone),
      payment_lock_active = false,
      payment_lock_amount_ksh = 0,
      payment_lock_reason = NULL,
      payment_lock_started_at = NULL
    WHERE id = v_payment.shop_id;
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'status', v_normalized_status,
    'mpesa_receipt', v_payment.mpesa_receipt,
    'subscription_ends_at', v_new_ends_at
  );
END;
$$;

REVOKE ALL ON FUNCTION public.confirm_subscription_payment(text, text, text, integer, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.confirm_subscription_payment(text, text, text, integer, text) TO service_role;

NOTIFY pgrst, 'reload schema';
