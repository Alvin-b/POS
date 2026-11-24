-- 202611240003_platform_billing_rpc_repair.sql
-- Targeted repair for projects where the platform billing settings RPC is
-- still absent from the PostgREST schema cache.

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

-- Remove a stale zero-argument definition before recreating it. This does not
-- affect any overload with parameters.
DROP FUNCTION IF EXISTS public.admin_get_platform_billing_settings();

CREATE FUNCTION public.admin_get_platform_billing_settings()
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

DROP FUNCTION IF EXISTS public.admin_update_platform_billing_settings(integer, integer);

CREATE FUNCTION public.admin_update_platform_billing_settings(
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
  ) VALUES (true, p_trial_duration_days, p_subscription_price_ksh, now(), auth.uid())
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

REVOKE ALL ON FUNCTION public.admin_get_platform_billing_settings() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_get_platform_billing_settings() TO authenticated;

REVOKE ALL ON FUNCTION public.admin_update_platform_billing_settings(integer, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_update_platform_billing_settings(integer, integer) TO authenticated;

NOTIFY pgrst, 'reload schema';
