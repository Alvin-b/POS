-- Keep owner-device subscription gates in sync with Superadmin payment locks.
-- The earlier get_shop_subscription_status RPC did not return payment-lock
-- columns, causing clients to fall back to the default KSh 2,500 amount.

CREATE OR REPLACE FUNCTION public.get_shop_subscription_status(p_shop_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_shop record;
    v_latest_checkout_request_id text;
    v_latest_payment_status text;
    v_is_entitled boolean;
    v_days_left integer := 0;
    v_status text;
BEGIN
    SELECT * INTO v_shop FROM public.shops WHERE id = p_shop_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('error', 'Shop not found');
    END IF;

    SELECT checkout_request_id, status
    INTO v_latest_checkout_request_id, v_latest_payment_status
    FROM public.subscription_payments
    WHERE shop_id = p_shop_id
    ORDER BY created_at DESC
    LIMIT 1;

    IF coalesce(v_shop.payment_lock_active, false) THEN
        v_status := 'payment_locked';
        v_is_entitled := false;
        v_days_left := 0;
    ELSIF v_shop.subscription_ends_at IS NOT NULL AND v_shop.subscription_ends_at > now() THEN
        v_status := 'active';
        v_is_entitled := true;
        v_days_left := ceil(extract(epoch from (v_shop.subscription_ends_at - now())) / 86400)::integer;
    ELSIF v_shop.trial_ends_at IS NOT NULL AND v_shop.trial_ends_at > now() THEN
        v_status := 'trial';
        v_is_entitled := true;
        v_days_left := ceil(extract(epoch from (v_shop.trial_ends_at - now())) / 86400)::integer;
    ELSE
        v_status := 'expired';
        v_is_entitled := false;
        v_days_left := 0;
    END IF;

    RETURN jsonb_build_object(
        'shop_id', v_shop.id,
        'shop_name', v_shop.name,
        'status', v_status,
        'is_entitled', v_is_entitled,
        'days_left', v_days_left,
        'trial_ends_at', v_shop.trial_ends_at,
        'subscription_ends_at', v_shop.subscription_ends_at,
        'subscription_price_ksh', v_shop.subscription_price_ksh,
        'subscription_plan_days', v_shop.subscription_plan_days,
        'last_payment_at', v_shop.last_payment_at,
        'last_payment_receipt', v_shop.last_payment_receipt,
        'latest_checkout_request_id', v_latest_checkout_request_id,
        'latest_payment_status', v_latest_payment_status,
        'payment_lock_active', coalesce(v_shop.payment_lock_active, false),
        'payment_lock_amount_ksh', coalesce(v_shop.payment_lock_amount_ksh, 0),
        'payment_lock_reason', v_shop.payment_lock_reason,
        'payment_lock_started_at', v_shop.payment_lock_started_at
    );
END;
$$;

-- Reopening a business from the Superadmin portfolio must also clear an
-- administrator payment lock. Subscription dates remain unchanged; this only
-- removes the explicit manual lock that blocks the owner device.
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
      closure_reason = CASE WHEN v_status = 'closed' THEN nullif(btrim(_reason), '') ELSE NULL END,
      payment_lock_active = CASE WHEN v_status = 'active' THEN false ELSE payment_lock_active END,
      payment_lock_amount_ksh = CASE WHEN v_status = 'active' THEN 0 ELSE payment_lock_amount_ksh END,
      payment_lock_reason = CASE WHEN v_status = 'active' THEN NULL ELSE payment_lock_reason END,
      payment_lock_started_at = CASE WHEN v_status = 'active' THEN NULL ELSE payment_lock_started_at END
  WHERE id = _shop
  RETURNING * INTO v_shop;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Business not found.';
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'shop_id', v_shop.id,
    'status', v_shop.status,
    'reason', coalesce(v_shop.suspension_reason, v_shop.closure_reason),
    'payment_lock_active', coalesce(v_shop.payment_lock_active, false),
    'payment_lock_amount_ksh', coalesce(v_shop.payment_lock_amount_ksh, 0)
  );
END;
$$;

REVOKE ALL ON FUNCTION public.get_shop_subscription_status(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_shop_subscription_status(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.admin_set_business_status(uuid, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_set_business_status(uuid, text, text) TO authenticated;

NOTIFY pgrst, 'reload schema';
