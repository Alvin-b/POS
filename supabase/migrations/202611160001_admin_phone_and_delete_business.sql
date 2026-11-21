-- Adds owner_phone output to admin_list_businesses() and adds superadmin-only admin_delete_business(uuid).

DROP FUNCTION IF EXISTS public.admin_list_businesses();
CREATE FUNCTION public.admin_list_businesses()
RETURNS TABLE(
  shop_id uuid,
  shop_name text,
  status text,
  business_type text,
  business_size text,
  feature_flags jsonb,
  owner_email text,
  owner_phone text,
  member_count bigint,
  product_count bigint,
  stock_units bigint,
  created_at timestamptz,
  suspended_at timestamptz,
  closed_at timestamptz,
  lifecycle_reason text,
  counter_inventory_mode text,
  counter_count bigint,
  subscription_status text,
  trial_ends_at timestamptz,
  subscription_ends_at timestamptz,
  subscription_price_ksh integer,
  subscription_plan_days integer,
  last_payment_at timestamptz,
  last_payment_receipt text,
  last_payment_phone text,
  custom_billing_note text,
  payment_lock_active boolean,
  payment_lock_amount_ksh integer,
  payment_lock_reason text,
  payment_lock_started_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.is_platform_admin() THEN
    RAISE EXCEPTION 'forbidden';
  END IF;

  RETURN QUERY
  SELECT
    s.id::uuid,
    s.name::text,
    s.status::text,
    s.business_type::text,
    s.business_size::text,
    s.feature_flags,
    coalesce(
      s.owner_email,
      (
        SELECT u.email
        FROM auth.users u
        JOIN public.shop_members om ON om.user_id = u.id
        WHERE om.shop_id = s.id AND om.role = 'owner' AND om.employment_status <> 'terminated'
        LIMIT 1
      ),
      'Pending owner enrollment'
    )::text,
    coalesce(s.owner_phone, s.last_payment_phone)::text,
    (
      SELECT count(*)::bigint
      FROM public.shop_members m
      WHERE m.shop_id = s.id
        AND m.employment_status <> 'terminated'
        AND NOT EXISTS (SELECT 1 FROM public.platform_admins pa WHERE pa.user_id = m.user_id)
    ),
    (
      SELECT count(*)::bigint
      FROM public.products p
      WHERE p.shop_id = s.id AND NOT p.archived
    ),
    (
      SELECT coalesce(sum(p.stock), 0)::bigint
      FROM public.products p
      WHERE p.shop_id = s.id AND NOT p.archived
    ),
    s.created_at::timestamptz,
    s.suspended_at::timestamptz,
    s.closed_at::timestamptz,
    coalesce(s.suspension_reason, s.closure_reason)::text,
    s.counter_inventory_mode::text,
    (
      SELECT count(*)::bigint
      FROM public.service_counters c
      WHERE c.shop_id = s.id AND c.active
    ),
    coalesce(s.subscription_status, 'trial')::text,
    s.trial_ends_at::timestamptz,
    s.subscription_ends_at::timestamptz,
    coalesce(s.subscription_price_ksh, 2500)::integer,
    coalesce(s.subscription_plan_days, 30)::integer,
    s.last_payment_at::timestamptz,
    s.last_payment_receipt::text,
    s.last_payment_phone::text,
    s.custom_billing_note::text,
    coalesce(s.payment_lock_active, false)::boolean,
    coalesce(s.payment_lock_amount_ksh, 0)::integer,
    s.payment_lock_reason::text,
    s.payment_lock_started_at::timestamptz
  FROM public.shops s
  ORDER BY s.created_at DESC;
END $$;

REVOKE ALL ON FUNCTION public.admin_list_businesses() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_list_businesses() TO authenticated;

CREATE OR REPLACE FUNCTION public.admin_delete_business(p_shop_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_shop record;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not_authenticated';
  END IF;
  IF NOT public.is_platform_admin() THEN
    RAISE EXCEPTION 'Only platform administrators can delete businesses.';
  END IF;

  SELECT id, name, owner_phone INTO v_shop
  FROM public.shops
  WHERE id = p_shop_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'business_not_found';
  END IF;

  DELETE FROM public.shops WHERE id = p_shop_id;

  RETURN jsonb_build_object(
    'success', true,
    'deleted_shop_id', v_shop.id,
    'deleted_shop_name', v_shop.name
  );
END $$;

REVOKE ALL ON FUNCTION public.admin_delete_business(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_delete_business(uuid) TO authenticated;

NOTIFY pgrst, 'reload schema';
