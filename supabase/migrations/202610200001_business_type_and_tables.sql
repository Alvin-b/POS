-- ==============================================================================
-- Migration: 202610200001_business_type_and_tables.sql
-- Purpose:
--   1. Schema for distinguishing 'Club' ('big_club') vs 'Liquor Store' ('counter_seller')
--   2. Enforces Table Management feature flag ONLY for Clubs
--   3. Configures multiple counters for Clubs vs 1 counter for Liquor Stores
--   4. Creates/updates dining_tables and table_orders tables
--   5. Updates RPCs: admin_create_business_profile, my_account, create_dining_table
-- ==============================================================================

-- 1. Ensure shops table has business_type, feature_flags, counter_count, and counter_inventory_mode
alter table if exists public.shops 
    add column if not exists business_type text not null default 'big_club',
    add column if not exists business_size text not null default 'medium',
    add column if not exists counter_inventory_mode text not null default 'shared',
    add column if not exists counter_count integer not null default 1,
    add column if not exists feature_flags jsonb not null default '{"tables": true, "employee_management": true, "working_hours": true, "fifo_inventory": true, "sales_analytics": true}'::jsonb;

-- Add check constraint for business_type if not present
do $$
begin
    if not exists (
        select 1 from pg_constraint 
        where conname = 'shops_business_type_check'
    ) then
        alter table public.shops 
            add constraint shops_business_type_check 
            check (business_type in ('big_club', 'club', 'liquor_store', 'counter_seller'));
    end if;
end $$;

-- 2. Dining tables and Table Orders tables (for Clubs)
create table if not exists public.dining_tables (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    name text not null,
    created_at timestamptz not null default now(),
    unique (shop_id, name)
);

create index if not exists dining_tables_shop_id_idx 
    on public.dining_tables (shop_id, created_at, name);

create table if not exists public.table_orders (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete cascade,
    table_id uuid not null references public.dining_tables(id) on delete cascade,
    sale_id uuid,
    product_id uuid not null,
    counter_id uuid,
    unit_name text not null,
    quantity integer not null check (quantity > 0),
    unit_price_ksh integer not null check (unit_price_ksh >= 0),
    total_ksh integer not null check (total_ksh >= 0),
    operation_id uuid unique,
    served_at timestamptz not null default now()
);

create index if not exists table_orders_shop_id_idx 
    on public.table_orders (shop_id, served_at);

create index if not exists table_orders_table_id_idx 
    on public.table_orders (table_id, served_at);

-- Row Level Security for dining_tables and table_orders
alter table public.dining_tables enable row level security;
alter table public.table_orders enable row level security;

-- Policy: Members can view dining tables in their active shop
do $$
begin
    if not exists (select 1 from pg_policies where tablename = 'dining_tables' and policyname = 'members_read_dining_tables') then
        create policy members_read_dining_tables on public.dining_tables
            for select
            using (
                exists (
                    select 1 from public.shop_members sm
                    where sm.shop_id = dining_tables.shop_id
                      and sm.user_id = auth.uid()
                      and sm.status = 'active'
                )
                or exists (
                    select 1 from public.profiles p
                    where p.id = auth.uid() and p.is_platform_admin = true
                )
            );
    end if;

    if not exists (select 1 from pg_policies where tablename = 'table_orders' and policyname = 'members_read_table_orders') then
        create policy members_read_table_orders on public.table_orders
            for select
            using (
                exists (
                    select 1 from public.shop_members sm
                    where sm.shop_id = table_orders.shop_id
                      and sm.user_id = auth.uid()
                      and sm.status = 'active'
                )
                or exists (
                    select 1 from public.profiles p
                    where p.id = auth.uid() and p.is_platform_admin = true
                )
            );
    end if;
end $$;

-- 3. RPC: admin_create_business_profile
-- Handles creating a new business as either 'Club' or 'Liquor Store',
-- automatically configuring Table Management and service counters.
create or replace function public.admin_create_business_profile(
    _name text,
    _owner_email text default null,
    _business_type text default 'big_club',
    _business_size text default 'medium',
    _counter_inventory_mode text default 'shared',
    _counter_count integer default 1
) returns jsonb
language plpgsql
security definer
as $$
declare
    v_is_admin boolean;
    v_new_shop_id uuid;
    v_normalized_type text;
    v_effective_counter_count integer;
    v_feature_flags jsonb;
    v_owner_user_id uuid;
    i integer;
begin
    -- 1. Security check: Only platform admins can invoke this
    select coalesce(is_platform_admin, false) into v_is_admin
    from public.profiles
    where id = auth.uid();

    if not coalesce(v_is_admin, false) then
        raise exception 'Only platform administrators can register new client businesses.';
    end if;

    if coalesce(trim(_name), '') = '' then
        raise exception 'Business name is required.';
    end if;

    -- 2. Normalize business type and compute feature flags
    if lower(trim(_business_type)) in ('counter_seller', 'liquor_store') then
        v_normalized_type := 'counter_seller';
        v_effective_counter_count := 1;
        -- Liquor Store: Sell/POS, FIFO Stock, Reports, Waiter management with hours.
        -- Table Management is strictly FALSE.
        v_feature_flags := jsonb_build_object(
            'tables', false,
            'employee_management', true,
            'working_hours', true,
            'fifo_inventory', true,
            'sales_analytics', true
        );
    else
        v_normalized_type := 'big_club';
        v_effective_counter_count := greatest(coalesce(_counter_count, 1), 1);
        -- Club: All features, including Table Management (TRUE by default) & multiple counters.
        v_feature_flags := jsonb_build_object(
            'tables', true,
            'employee_management', true,
            'working_hours', true,
            'fifo_inventory', true,
            'sales_analytics', true
        );
    end if;

    -- 3. Create the Shop
    insert into public.shops (
        name,
        business_type,
        business_size,
        counter_inventory_mode,
        counter_count,
        feature_flags,
        status,
        created_at
    ) values (
        trim(_name),
        v_normalized_type,
        coalesce(_business_size, 'medium'),
        coalesce(_counter_inventory_mode, 'shared'),
        v_effective_counter_count,
        v_feature_flags,
        'active',
        now()
    )
    returning id into v_new_shop_id;

    -- 4. Create Service Counters
    if v_normalized_type = 'counter_seller' then
        insert into public.service_counters (shop_id, name, active, created_at)
        values (v_new_shop_id, 'Main Counter', true, now());
    else
        if v_effective_counter_count = 1 then
            insert into public.service_counters (shop_id, name, active, created_at)
            values (v_new_shop_id, 'Main Counter', true, now());
        else
            for i in 1..v_effective_counter_count loop
                insert into public.service_counters (shop_id, name, active, created_at)
                values (v_new_shop_id, 'Counter ' || i, true, now());
            end loop;
        end if;

        -- Create starter Dining Tables for Clubs by default
        insert into public.dining_tables (shop_id, name, created_at) values
            (v_new_shop_id, 'Table 1', now()),
            (v_new_shop_id, 'Table 2', now()),
            (v_new_shop_id, 'Table 3', now()),
            (v_new_shop_id, 'VIP 1', now()),
            (v_new_shop_id, 'VIP 2', now());
    end if;

    -- 5. Attach Owner if email was provided
    if _owner_email is not null and trim(_owner_email) != '' then
        select id into v_owner_user_id
        from auth.users
        where lower(email) = lower(trim(_owner_email));

        if v_owner_user_id is not null then
            insert into public.shop_members (shop_id, user_id, role, status, created_at)
            values (v_new_shop_id, v_owner_user_id, 'owner', 'active', now())
            on conflict (shop_id, user_id) do update set
                role = 'owner',
                status = 'active';
        end if;
    end if;

    -- 6. Return response
    return jsonb_build_object(
        'shop_id', v_new_shop_id,
        'id', v_new_shop_id,
        'name', trim(_name),
        'business_type', v_normalized_type,
        'feature_flags', v_feature_flags,
        'counter_count', v_effective_counter_count
    );
end;
$$;

-- 4. RPC: create_dining_table
-- Ensures only Clubs with 'tables': true can create dining tables
create or replace function public.create_dining_table(
    _shop uuid,
    _name text
) returns jsonb
language plpgsql
security definer
as $$
declare
    v_caller_role text;
    v_supports_tables boolean;
    v_table_id uuid;
    v_created_at timestamptz;
begin
    -- Check role
    select role into v_caller_role
    from public.shop_members
    where shop_id = _shop and user_id = auth.uid() and status = 'active';

    if v_caller_role not in ('owner', 'manager') then
        raise exception 'Only a business owner or manager can add tables.';
    end if;

    -- Check business type / feature flag
    select coalesce((feature_flags->>'tables')::boolean, false) into v_supports_tables
    from public.shops
    where id = _shop;

    if not coalesce(v_supports_tables, false) then
        raise exception 'Table management is not enabled for this business type (Liquor Stores use direct counter checkout).';
    end if;

    if coalesce(trim(_name), '') = '' then
        raise exception 'Table name cannot be empty.';
    end if;

    insert into public.dining_tables (shop_id, name, created_at)
    values (_shop, trim(_name), now())
    returning id, created_at into v_table_id, v_created_at;

    return jsonb_build_object(
        'id', v_table_id,
        'shop_id', _shop,
        'name', trim(_name),
        'created_at', v_created_at
    );
end;
$$;

notify pgrst, 'reload schema';
