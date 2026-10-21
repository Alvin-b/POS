-- Remove only empty tables that were inserted by the old business provisioning seed.
-- User-created tables with the same names are preserved when they contain orders.
delete from public.dining_tables as t
where lower(trim(t.name)) in ('table 1', 'table 2', 'table 3', 'vip 1', 'vip 2')
  and not exists (
      select 1
      from public.table_orders as o
      where o.table_id = t.id
  );

notify pgrst, 'reload schema';
