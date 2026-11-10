-- 202611100001_saas_daraja_subscriptions.sql
-- SaaS Subscription & Daraja M-Pesa STK Push Billing Schema

-- 1. Extend shops with SaaS subscription columns
ALTER TABLE public.shops
    ADD COLUMN IF NOT EXISTS trial_ends_at timestamptz DEFAULT (now() + interval '14 days'),
    ADD COLUMN IF NOT EXISTS subscription_ends_at timestamptz,
    ADD COLUMN IF NOT EXISTS subscription_status text NOT NULL DEFAULT 'trial',
    ADD COLUMN IF NOT EXISTS subscription_price_ksh integer NOT NULL DEFAULT 2500,
    ADD COLUMN IF NOT EXISTS subscription_plan_days integer NOT NULL DEFAULT 30,
    ADD COLUMN IF NOT EXISTS last_payment_at timestamptz,
    ADD COLUMN IF NOT EXISTS last_payment_receipt text,
    ADD COLUMN IF NOT EXISTS last_payment_phone text,
    ADD COLUMN IF NOT EXISTS custom_billing_note text;

-- Set default trial_ends_at for existing shops if null
UPDATE public.shops
SET trial_ends_at = coalesce(trial_ends_at, created_at + interval '14 days', now() + interval '14 days')
WHERE trial_ends_at IS NULL;

-- 2. Create subscription payments log table for Daraja STK Push transactions
CREATE TABLE IF NOT EXISTS public.subscription_payments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id uuid NOT NULL REFERENCES public.shops(id) ON DELETE CASCADE,
    checkout_request_id text UNIQUE,
    merchant_request_id text,
    phone text NOT NULL,
    amount_ksh integer NOT NULL,
    plan_days integer NOT NULL DEFAULT 30,
    status text NOT NULL DEFAULT 'pending', -- 'pending', 'completed', 'failed', 'cancelled'
    mpesa_receipt text,
    result_code integer,
    result_desc text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS subscription_payments_shop_idx ON public.subscription_payments(shop_id, created_at DESC);
CREATE INDEX IF NOT EXISTS subscription_payments_checkout_idx ON public.subscription_payments(checkout_request_id);

ALTER TABLE public.subscription_payments ENABLE ROW LEVEL SECURITY;

-- 3. Superadmin RPC: Update business subscription pricing, date and status
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
AS $$
DECLARE
    v_shop record;
BEGIN
    UPDATE public.shops
    SET
        subscription_price_ksh = coalesce(p_price_ksh, subscription_price_ksh),
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

-- 4. RPC to initiate STK record
CREATE OR REPLACE FUNCTION public.initiate_subscription_payment_record(
    p_shop_id uuid,
    p_phone text,
    p_amount_ksh integer,
    p_checkout_request_id text,
    p_merchant_request_id text DEFAULT NULL,
    p_plan_days integer DEFAULT 30
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_payment record;
BEGIN
    INSERT INTO public.subscription_payments (
        shop_id,
        phone,
        amount_ksh,
        checkout_request_id,
        merchant_request_id,
        plan_days,
        status
    )
    VALUES (
        p_shop_id,
        p_phone,
        p_amount_ksh,
        p_checkout_request_id,
        p_merchant_request_id,
        coalesce(p_plan_days, 30),
        'pending'
    )
    RETURNING * INTO v_payment;

    RETURN jsonb_build_object(
        'payment_id', v_payment.id,
        'checkout_request_id', v_payment.checkout_request_id,
        'status', v_payment.status
    );
END;
$$;

-- 5. RPC to confirm subscription payment and extend entitlement
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
AS $$
DECLARE
    v_payment record;
    v_shop record;
    v_new_ends_at timestamptz;
    v_days integer;
BEGIN
    UPDATE public.subscription_payments
    SET
        status = p_status,
        mpesa_receipt = p_mpesa_receipt,
        result_code = p_result_code,
        result_desc = p_result_desc,
        updated_at = now()
    WHERE checkout_request_id = p_checkout_request_id
    RETURNING * INTO v_payment;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'Payment request not found');
    END IF;

    IF p_status = 'completed' THEN
        SELECT * INTO v_shop FROM public.shops WHERE id = v_payment.shop_id;
        v_days := coalesce(v_payment.plan_days, v_shop.subscription_plan_days, 30);

        -- Extend from current subscription_ends_at if still in the future, or from now()
        v_new_ends_at := coalesce(
            greatest(v_shop.subscription_ends_at, now()),
            now()
        ) + (v_days || ' days')::interval;

        UPDATE public.shops
        SET
            subscription_status = 'active',
            subscription_ends_at = v_new_ends_at,
            last_payment_at = now(),
            last_payment_receipt = p_mpesa_receipt,
            last_payment_phone = v_payment.phone
        WHERE id = v_payment.shop_id;
    END IF;

    RETURN jsonb_build_object(
        'success', true,
        'status', p_status,
        'mpesa_receipt', p_mpesa_receipt,
        'subscription_ends_at', v_new_ends_at
    );
END;
$$;

-- 6. RPC to check subscription status
CREATE OR REPLACE FUNCTION public.get_shop_subscription_status(p_shop_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_shop record;
    v_latest_payment record;
    v_is_entitled boolean;
    v_days_left integer := 0;
    v_status text;
BEGIN
    SELECT * INTO v_shop FROM public.shops WHERE id = p_shop_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('error', 'Shop not found');
    END IF;

    SELECT * INTO v_latest_payment
    FROM public.subscription_payments
    WHERE shop_id = p_shop_id
    ORDER BY created_at DESC
    LIMIT 1;

    -- Evaluate entitlement
    IF v_shop.subscription_ends_at IS NOT NULL AND v_shop.subscription_ends_at > now() THEN
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
        'latest_checkout_request_id', v_latest_payment.checkout_request_id,
        'latest_payment_status', v_latest_payment.status
    );
END;
$$;

NOTIFY pgrst, 'reload schema';
