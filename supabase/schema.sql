-- Aerix Launcher social layer — schema for the free Supabase tier.
--
-- Paste this whole file into Supabase -> SQL Editor -> New query -> Run, once.
-- It is idempotent: running it again does not break anything.
--
-- What it holds: one profile per signed-in user, the friend graph (with requests), and direct
-- messages. Nothing else. No images are stored here (Minecraft heads come from a CDN), and
-- messages older than 90 days are deleted automatically, so the 500 MB free tier stays flat
-- instead of filling up.

-- ---- profiles ----------------------------------------------------------------------------

create table if not exists public.profiles (
    id            uuid primary key references auth.users (id) on delete cascade,
    display_name  text,
    avatar_url    text,
    mc_username   text,
    -- Bumped by the app while the game or launcher is open; used for the online dot.
    last_seen     timestamptz not null default now(),
    created_at    timestamptz not null default now()
);

alter table public.profiles enable row level security;

-- Anyone signed in may read a profile (that is what makes a friend list possible).
drop policy if exists profiles_read on public.profiles;
create policy profiles_read on public.profiles
    for select to authenticated using (true);

-- A user may only create and change their own profile.
drop policy if exists profiles_insert on public.profiles;
create policy profiles_insert on public.profiles
    for insert to authenticated with check (auth.uid() = id);

drop policy if exists profiles_update on public.profiles;
create policy profiles_update on public.profiles
    for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);

-- ---- friendships -------------------------------------------------------------------------
-- One row per pair, in the direction the request was sent. 'accepted' makes them friends both
-- ways; 'declined' is kept so a declined request cannot be re-sent in a loop.

create table if not exists public.friendships (
    requester  uuid not null references public.profiles (id) on delete cascade,
    addressee  uuid not null references public.profiles (id) on delete cascade,
    status     text not null default 'pending' check (status in ('pending', 'accepted', 'declined', 'blocked')),
    created_at timestamptz not null default now(),
    primary key (requester, addressee),
    check (requester <> addressee)
);

create index if not exists friendships_addressee_idx on public.friendships (addressee, status);
create index if not exists friendships_requester_idx on public.friendships (requester, status);

alter table public.friendships enable row level security;

-- A user sees the requests they sent and the ones they received, nothing else.
drop policy if exists friendships_read on public.friendships;
create policy friendships_read on public.friendships
    for select to authenticated using (auth.uid() = requester or auth.uid() = addressee);

-- Only the requester creates a request, and always as 'pending'.
drop policy if exists friendships_insert on public.friendships;
create policy friendships_insert on public.friendships
    for insert to authenticated with check (auth.uid() = requester and status = 'pending');

-- Either side may answer it (accept, decline), and either may remove the friendship.
drop policy if exists friendships_update on public.friendships;
create policy friendships_update on public.friendships
    for update to authenticated using (auth.uid() = requester or auth.uid() = addressee);

drop policy if exists friendships_delete on public.friendships;
create policy friendships_delete on public.friendships
    for delete to authenticated using (auth.uid() = requester or auth.uid() = addressee);

-- ---- direct messages ---------------------------------------------------------------------

create table if not exists public.messages (
    id          bigint generated always as identity primary key,
    sender      uuid not null references public.profiles (id) on delete cascade,
    recipient   uuid not null references public.profiles (id) on delete cascade,
    body        text not null check (char_length(body) between 1 and 2000),
    created_at  timestamptz not null default now()
);

-- The conversation query: "messages between me and X, newest first".
create index if not exists messages_pair_idx on public.messages (sender, recipient, created_at desc);
create index if not exists messages_inbox_idx on public.messages (recipient, created_at desc);

alter table public.messages enable row level security;

-- Only the two people in a conversation can read it.
drop policy if exists messages_read on public.messages;
create policy messages_read on public.messages
    for select to authenticated using (auth.uid() = sender or auth.uid() = recipient);

-- You may only send as yourself.
drop policy if exists messages_insert on public.messages;
create policy messages_insert on public.messages
    for insert to authenticated with check (auth.uid() = sender);

-- ---- retention ---------------------------------------------------------------------------
-- Called by the app (or a nightly job) to keep the table small on the free tier.

create or replace function public.prune_messages() returns void
language sql security definer as $$
    delete from public.messages where created_at < now() - interval '90 days';
$$;

-- ---- presence helper ---------------------------------------------------------------------
-- Keeps profiles.last_seen current without giving the app write access to other people's rows.

create or replace function public.touch_presence() returns void
language sql security definer as $$
    update public.profiles set last_seen = now() where id = auth.uid();
$$;
