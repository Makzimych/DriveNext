-- ЛР3. Серверный каталог учебных автомобилей и доступ для вошедших пользователей.
-- Учебный каталог ЛР3. Запрос допускает повторное выполнение.
create table if not exists public.cars (
 id text primary key, brand text not null, model text not null,
 price integer not null check (price > 0), transmission text not null,
 fuel text not null, seats integer not null check(seats > 0),
 year integer not null, description text not null default '',
 search_text text generated always as (brand || ' ' || model) stored
);
alter table public.cars enable row level security;
revoke all on public.cars from anon;
grant select on public.cars to authenticated;
do $$ begin
 if not exists(select 1 from pg_policies where schemaname='public' and tablename='cars' and policyname='Authenticated catalog') then
  create policy "Authenticated catalog" on public.cars for select to authenticated using (true);
 end if;
end $$;
insert into public.cars (id,brand,model,price,transmission,fuel,seats,year,description) values
('lada-vesta','Lada','Vesta',2500,'Механика','Бензин',5,2023,'Практичный седан для поездок по городу.'),
('kia-rio','Kia','Rio',3100,'Автомат','Бензин',5,2022,'Компактный автомобиль с автоматической коробкой.'),
('hyundai-solaris','Hyundai','Solaris',3000,'Автомат','Бензин',5,2022,'Экономичный седан для ежедневных поездок.'),
('toyota-camry','Toyota','Camry',5500,'Автомат','Бензин',5,2023,'Комфортный седан бизнес-класса.'),
('renault-duster','Renault','Duster',3900,'Механика','Бензин',5,2021,'Вместительный кроссовер.'),
('skoda-octavia','Skoda','Octavia',4200,'Автомат','Бензин',5,2022,'Лифтбек с просторным багажником.')
on conflict(id) do nothing;
