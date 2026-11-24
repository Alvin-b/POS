-- QR enrollment station metadata and token compatibility.
-- Apply after 202611040006_qr_reenrollment_identity.sql.
-- The Edge Functions should write/read access_challenges.station and return it
-- from poll-access-challenge for the receiving device.

ALTER TABLE public.access_challenges
  ADD COLUMN IF NOT EXISTS station text NOT NULL DEFAULT 'waiter';

ALTER TABLE public.access_device_identities
  ADD COLUMN IF NOT EXISTS station text NOT NULL DEFAULT 'waiter';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'access_challenges_station_check'
  ) THEN
    ALTER TABLE public.access_challenges
      ADD CONSTRAINT access_challenges_station_check
      CHECK (station IN ('waiter', 'butchery'));
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'access_device_identities_station_check'
  ) THEN
    ALTER TABLE public.access_device_identities
      ADD CONSTRAINT access_device_identities_station_check
      CHECK (station IN ('waiter', 'butchery'));
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS access_challenges_station_status_idx
  ON public.access_challenges(shop_id, station, status, created_at DESC);

CREATE INDEX IF NOT EXISTS access_device_identities_shop_station_idx
  ON public.access_device_identities(shop_id, station, updated_at DESC);

-- The QR image may contain CPR1|<token>, while the Edge Functions should
-- use only <token> for database lookup. This helper is safe to call from
-- service-role Edge Functions and keeps both old and new QR payloads valid.
CREATE OR REPLACE FUNCTION public.normalize_access_challenge_token(_payload text)
RETURNS text
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
SET search_path = pg_catalog, public, pg_temp
AS $$
  SELECT btrim(
    CASE
      WHEN lower(left(coalesce(_payload, ''), 5)) = 'cpr1|' THEN substring(_payload FROM 6)
      ELSE coalesce(_payload, '')
    END
  )
$$;

REVOKE ALL ON FUNCTION public.normalize_access_challenge_token(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.normalize_access_challenge_token(text) TO service_role;

NOTIFY pgrst, 'reload schema';
