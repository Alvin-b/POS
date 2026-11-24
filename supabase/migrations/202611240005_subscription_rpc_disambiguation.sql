-- Remove the legacy five-parameter overload. Its presence alongside the
-- defaulted seven-parameter function makes PostgREST report an ambiguous RPC
-- when updating subscription price, status, plan days, or trial dates.
DROP FUNCTION IF EXISTS public.admin_update_shop_subscription(uuid, integer, text, integer, timestamptz);

GRANT EXECUTE ON FUNCTION public.admin_update_shop_subscription(uuid, integer, timestamptz, text, integer, timestamptz, text)
  TO authenticated;

NOTIFY pgrst, 'reload schema';
