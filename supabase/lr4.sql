-- ЛР4. Адрес автомобиля, стоимость страховки и возвращаемый депозит.
alter table public.cars
    add column if not exists address text not null default 'Москва, Авиамоторная ул., 8, стр. 2',
    add column if not exists insurance_per_day bigint not null default 300 check (insurance_per_day >= 0),
    add column if not exists deposit bigint not null default 15000 check (deposit >= 0);
