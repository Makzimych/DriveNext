-- Run once in Supabase SQL Editor. Contains no credentials.
create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    surname text not null,
    name text not null,
    patronymic text not null default '',
    birthday date not null,
    gender text not null check (gender in ('male', 'female')),
    license_number text not null check (char_length(license_number) = 10),
    license_issued date not null,
    avatar text,
    license_photo text not null,
    passport_photo text not null,
    created_at timestamptz not null default now()
);
alter table public.profiles enable row level security;
grant select, insert, update on public.profiles to authenticated;
revoke all on public.profiles from anon;
create policy "Read own profile" on public.profiles for select to authenticated using ((select auth.uid()) = id);
create policy "Insert own profile" on public.profiles for insert to authenticated with check ((select auth.uid()) = id);
create policy "Update own profile" on public.profiles for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('identity-documents', 'identity-documents', false, 10485760, array['image/jpeg'])
on conflict (id) do update set public = false, file_size_limit = 10485760, allowed_mime_types = array['image/jpeg'];
create policy "Read own documents" on storage.objects for select to authenticated
using (bucket_id = 'identity-documents' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Insert own documents" on storage.objects for insert to authenticated
with check (bucket_id = 'identity-documents' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Update own documents" on storage.objects for update to authenticated
using (bucket_id = 'identity-documents' and (storage.foldername(name))[1] = (select auth.uid())::text)
with check (bucket_id = 'identity-documents' and (storage.foldername(name))[1] = (select auth.uid())::text);
