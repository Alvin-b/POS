-- Counter POS Tables schema
-- Run this in the Supabase SQL editor, or apply it through Supabase migrations.
-- The app can still open POS with passkeys before this migration is applied,
-- but Tables and QR pairing depend on these public tables.

create table if not exists public.dining_tables (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    name text not null,
    created_at timestamptz not null default now(),
    unique (shop_id, name)
);

create index if not exists dining_tables_shop_id_idx
    on public.dining_tables (shop_id, created_at, name);

create table if not exists public.table_orders (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    table_id uuid not null references public.dining_tables(id) on delete cascade,
    sale_id uuid,
    product_id uuid not null,
    counter_id uuid,
    unit_name text not null,
    quantity integer not null check (quantity > 0),
    unit_price_ksh integer not null check (unit_price_ksh >= 0),
    total_ksh integer not null check (total_ksh >= 0),
    operation_id uuid,
    served_at timestamptz not null default now(),
    unique (operation_id)
);

create index if not exists table_orders_shop_id_idx
    on public.table_orders (shop_id, served_at);
create index if not exists table_orders_table_id_idx
    on public.table_orders (table_id, served_at);

-- Keep RLS enabled. Add the same tenant-membership policies used by the
-- existing products, sales, and service_counters tables in this project before
-- exposing direct REST reads. The SECURITY DEFINER RPCs remain responsible for
-- tenant and role authorization for writes.
alter table public.dining_tables enable row level security;
alter table public.table_orders enable row level security;

notify pgrst, 'reload schema';
