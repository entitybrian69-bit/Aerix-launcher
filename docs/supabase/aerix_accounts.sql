-- ============================================================================
--  Aerix accounts — Supabase schema
--
--  Paste this whole file into Supabase  →  SQL Editor  →  New query  →  Run.
--  It is idempotent: running it twice is safe.
--
--  What it creates
--    public.profiles  one row per Aerix account, owned by that account only
--    a trigger        creates the profile row the moment someone signs up
--    RLS policies     a signed-in person can read and update only their own row
--
--  A deleted account is a row with deleted_at set. Signing in again with the
--  correct password is what restores it, so nothing is ever silently lost.
-- ============================================================================

create table if not exists public.profiles (
    id          uuid primary key references auth.users (id) on delete cascade,
    username    text not null,
    email       text not null,
    deleted_at  timestamptz,
    created_at  timestamptz not null default now()
);

-- Case-insensitive uniqueness: "Aerix" and "aerix" are the same name.
create unique index if not exists profiles_username_lower_key
    on public.profiles (lower(username));

alter table public.profiles enable row level security;

drop policy if exists "profiles read own"   on public.profiles;
drop policy if exists "profiles insert own" on public.profiles;
drop policy if exists "profiles update own" on public.profiles;

create policy "profiles read own"
    on public.profiles for select
    using (auth.uid() = id);

create policy "profiles insert own"
    on public.profiles for insert
    with check (auth.uid() = id);

create policy "profiles update own"
    on public.profiles for update
    using (auth.uid() = id)
    with check (auth.uid() = id);

-- ---------------------------------------------------------------------------
--  Create the profile row when someone signs up.
--  security definer so the insert is allowed even though RLS is on.
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_aerix_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, username, email)
    values (
        new.id,
        coalesce(
            nullif(new.raw_user_meta_data ->> 'username', ''),
            split_part(new.email, '@', 1)
        ),
        new.email
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created_aerix on auth.users;
create trigger on_auth_user_created_aerix
    after insert on auth.users
    for each row execute function public.handle_new_aerix_user();

-- ---------------------------------------------------------------------------
--  Optional: does this username already exist? Safe to call before signup,
--  because it only answers yes/no and leaks nothing else.
-- ---------------------------------------------------------------------------
create or replace function public.aerix_username_taken(candidate text)
returns boolean
language sql
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.profiles where lower(username) = lower(candidate)
    );
$$;

grant execute on function public.aerix_username_taken(text) to anon, authenticated;
