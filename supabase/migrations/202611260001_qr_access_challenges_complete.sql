-- Complete idempotent schema for QR device enrollment challenges & identities.
-- Fixes "could_not_read_qr" caused by missing columns (station, station_name,
-- processing_at, device_key_hash, device_id) on older or partially migrated databases.

CREATE TABLE IF NOT EXISTS public.access_challenges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  token_hash text NOT NULL UNIQUE,
  device_key_hash text,
  device_id text,
  status text NOT NULL DEFAULT 'pending',
  shop_id uuid REFERENCES public.shops(id) ON DELETE CASCADE,
  user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  approved_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  role public.shop_role,
  station text NOT NULL DEFAULT 'waiter',
  station_name text,
  processing_at timestamptz,
  expires_at timestamptz NOT NULL DEFAULT (now() + interval '10 minutes'),
  approved_at timestamptz,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.access_challenges
  ADD COLUMN IF NOT EXISTS device_key_hash text,
  ADD COLUMN IF NOT EXISTS device_id text,
  ADD COLUMN IF NOT EXISTS status text NOT NULL DEFAULT 'pending',
  ADD COLUMN IF NOT EXISTS shop_id uuid REFERENCES public.shops(id) ON DELETE CASCADE,
  ADD COLUMN IF NOT EXISTS user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  ADD COLUMN IF NOT EXISTS approved_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS role public.shop_role,
  ADD COLUMN IF NOT EXISTS station text NOT NULL DEFAULT 'waiter',
  ADD COLUMN IF NOT EXISTS station_name text,
  ADD COLUMN IF NOT EXISTS processing_at timestamptz,
  ADD COLUMN IF NOT EXISTS expires_at timestamptz NOT NULL DEFAULT (now() + interval '10 minutes'),
  ADD COLUMN IF NOT EXISTS approved_at timestamptz,
  ADD COLUMN IF NOT EXISTS consumed_at timestamptz,
  ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS public.access_device_identities (
  device_key_hash text PRIMARY KEY,
  shop_id uuid NOT NULL REFERENCES public.shops(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  role public.shop_role NOT NULL,
  station text NOT NULL DEFAULT 'waiter',
  station_name text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (shop_id, user_id, role)
);

ALTER TABLE public.access_device_identities
  ADD COLUMN IF NOT EXISTS station text NOT NULL DEFAULT 'waiter',
  ADD COLUMN IF NOT EXISTS station_name text,
  ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'access_challenges_station_check'
      AND conrelid = 'public.access_challenges'::regclass
  ) THEN
    ALTER TABLE public.access_challenges
      ADD CONSTRAINT access_challenges_station_check
      CHECK (station IN ('waiter', 'butchery'));
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'access_device_identities_station_check'
      AND conrelid = 'public.access_device_identities'::regclass
  ) THEN
    ALTER TABLE public.access_device_identities
      ADD CONSTRAINT access_device_identities_station_check
      CHECK (station IN ('waiter', 'butchery'));
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS access_challenges_processing_idx
  ON public.access_challenges(status, processing_at);

CREATE INDEX IF NOT EXISTS access_challenges_device_key_idx
  ON public.access_challenges(device_key_hash, shop_id, role, status);

CREATE INDEX IF NOT EXISTS access_challenges_station_status_idx
  ON public.access_challenges(shop_id, station, status, created_at DESC);

CREATE INDEX IF NOT EXISTS access_device_identities_shop_station_idx
  ON public.access_device_identities(shop_id, station, updated_at DESC);

ALTER TABLE public.access_challenges ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.access_challenges FROM PUBLIC, anon, authenticated;
GRANT ALL ON public.access_challenges TO service_role;

ALTER TABLE public.access_device_identities ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.access_device_identities FROM PUBLIC, anon, authenticated;
GRANT ALL ON public.access_device_identities TO service_role;

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
