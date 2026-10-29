-- Aerix social layer, step 1 of 3 - tables, columns and indexes.
--
-- Paste ONE file at a time into Supabase -> SQL Editor -> New query -> Run, then move on to the
-- next one. Each file on its own is small enough to copy on a phone without cutting a statement
-- in half, and every statement is idempotent, so running a step twice changes nothing.

-- Creates the profile columns, the friend graph, follows, blocks, reports, ranks, the party
-- tables and the image bucket. No policies and no functions yet.

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

-- ==========================================================================================
-- Part 2 — profiles, follows, ranks, blocks and image storage.
--
-- Paste this whole file again after an update: every statement below is idempotent, so running
-- it a second time only adds what is missing.
-- ==========================================================================================

-- ---- profile fields ----------------------------------------------------------------------

alter table public.profiles add column if not exists username text;

alter table public.profiles add column if not exists name text;

alter table public.profiles add column if not exists country text;

alter table public.profiles add column if not exists bio text;

alter table public.profiles add column if not exists banner_url text;

alter table public.profiles add column if not exists cape_url text;

alter table public.profiles add column if not exists skin_url text;

alter table public.profiles add column if not exists status_text text;

alter table public.profiles add column if not exists looking_to_play boolean not null default false;

alter table public.profiles add column if not exists onboarded boolean not null default false;

alter table public.profiles add column if not exists username_changed_at timestamptz;

-- A username is unique regardless of case, and must hold at least one letter and one number.
create unique index if not exists profiles_username_key on public.profiles (lower(username)) where username is not null;

alter table public.profiles drop constraint if exists profiles_username_format;

alter table public.profiles add constraint profiles_username_format check (
    username is null or (
        username ~ '^[A-Za-z0-9_]{3,20}$' and username ~ '[A-Za-z]' and username ~ '[0-9]'
    )
);

alter table public.profiles drop constraint if exists profiles_bio_length;

alter table public.profiles add constraint profiles_bio_length check (bio is null or char_length(bio) <= 300);

-- ---- follows -----------------------------------------------------------------------------

create table if not exists public.follows (
    follower   uuid not null references public.profiles (id) on delete cascade,
    following  uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (follower, following),
    check (follower <> following)
);

create index if not exists follows_following_idx on public.follows (following);

alter table public.follows enable row level security;

-- ---- blocks and reports ------------------------------------------------------------------

create table if not exists public.blocks (
    blocker    uuid not null references public.profiles (id) on delete cascade,
    blocked    uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker, blocked),
    check (blocker <> blocked)
);

alter table public.blocks enable row level security;

create table if not exists public.reports (
    id         bigint generated always as identity primary key,
    reporter   uuid not null references public.profiles (id) on delete cascade,
    target     uuid not null references public.profiles (id) on delete cascade,
    reason     text not null check (char_length(reason) between 3 and 500),
    created_at timestamptz not null default now()
);

alter table public.reports enable row level security;

-- ---- ranks -------------------------------------------------------------------------------
-- Achievements are worked out from the data itself (see profile_card). This table is only for
-- ranks you hand out by hand, for example the first testers.

create table if not exists public.rank_grants (
    user_id    uuid not null references public.profiles (id) on delete cascade,
    rank_key   text not null,
    granted_at timestamptz not null default now(),
    primary key (user_id, rank_key)
);

alter table public.rank_grants enable row level security;

-- ---- message extras ----------------------------------------------------------------------

alter table public.messages add column if not exists read_at timestamptz;

alter table public.messages add column if not exists reply_to bigint references public.messages (id) on delete set null;

alter table public.messages add column if not exists attachment_url text;

alter table public.messages add column if not exists edited_at timestamptz;

-- ---- parties (schema now, screens in the next build) --------------------------------------

create table if not exists public.parties (
    id         uuid primary key default gen_random_uuid(),
    host       uuid not null references public.profiles (id) on delete cascade,
    title      text,
    instance   text,
    version    text,
    mc_version text,
    expires_at timestamptz not null default now() + interval '2 hours',
    created_at timestamptz not null default now()
);

create table if not exists public.party_members (
    party_id  uuid not null references public.parties (id) on delete cascade,
    member    uuid not null references public.profiles (id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (party_id, member)
);

alter table public.parties enable row level security;

alter table public.party_members enable row level security;

-- ---- image storage -----------------------------------------------------------------------

insert into storage.buckets (id, name, public)
values ('social-media', 'social-media', true)
on conflict (id) do update set public = true;
