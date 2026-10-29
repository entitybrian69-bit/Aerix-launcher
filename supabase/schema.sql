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
language sql security definer as '    delete from public.messages where created_at < now() - interval ''90 days'';';

-- ---- presence helper ---------------------------------------------------------------------
-- Keeps profiles.last_seen current without giving the app write access to other people's rows.

create or replace function public.touch_presence() returns void
language sql security definer as '    update public.profiles set last_seen = now() where id = auth.uid();';

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

-- ---- who can read a profile --------------------------------------------------------------
-- A profile is only readable by its owner and by people already connected to them - a friend, or
-- someone either side follows. There is no directory to list: the only way to reach a stranger is
-- to type their exact username, which goes through find_player() below.

drop policy if exists profiles_read on public.profiles;
drop policy if exists profiles_read_related on public.profiles;
create policy profiles_read_related on public.profiles
    for select to authenticated using (
        id = auth.uid()
        or exists (
            select 1 from public.friendships f
            where (f.requester = auth.uid() and f.addressee = profiles.id)
               or (f.addressee = auth.uid() and f.requester = profiles.id)
        )
        or exists (
            select 1 from public.follows fo
            where (fo.follower = auth.uid() and fo.following = profiles.id)
               or (fo.following = auth.uid() and fo.follower = profiles.id)
        )
    );

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

drop policy if exists follows_read on public.follows;
create policy follows_read on public.follows
    for select to authenticated using (true);

drop policy if exists follows_insert on public.follows;
create policy follows_insert on public.follows
    for insert to authenticated with check (auth.uid() = follower);

drop policy if exists follows_delete on public.follows;
create policy follows_delete on public.follows
    for delete to authenticated using (auth.uid() = follower);

-- ---- blocks and reports ------------------------------------------------------------------

create table if not exists public.blocks (
    blocker    uuid not null references public.profiles (id) on delete cascade,
    blocked    uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker, blocked),
    check (blocker <> blocked)
);

alter table public.blocks enable row level security;

drop policy if exists blocks_read on public.blocks;
create policy blocks_read on public.blocks
    for select to authenticated using (auth.uid() = blocker);

drop policy if exists blocks_write on public.blocks;
create policy blocks_write on public.blocks
    for insert to authenticated with check (auth.uid() = blocker);

drop policy if exists blocks_delete on public.blocks;
create policy blocks_delete on public.blocks
    for delete to authenticated using (auth.uid() = blocker);

create table if not exists public.reports (
    id         bigint generated always as identity primary key,
    reporter   uuid not null references public.profiles (id) on delete cascade,
    target     uuid not null references public.profiles (id) on delete cascade,
    reason     text not null check (char_length(reason) between 3 and 500),
    created_at timestamptz not null default now()
);

alter table public.reports enable row level security;

drop policy if exists reports_insert on public.reports;
create policy reports_insert on public.reports
    for insert to authenticated with check (auth.uid() = reporter);

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

drop policy if exists rank_grants_read on public.rank_grants;
create policy rank_grants_read on public.rank_grants
    for select to authenticated using (auth.uid() = user_id);

-- ---- message extras ----------------------------------------------------------------------

alter table public.messages add column if not exists read_at timestamptz;
alter table public.messages add column if not exists reply_to bigint references public.messages (id) on delete set null;
alter table public.messages add column if not exists attachment_url text;
alter table public.messages add column if not exists edited_at timestamptz;

drop policy if exists messages_update on public.messages;
create policy messages_update on public.messages
    for update to authenticated
    using (auth.uid() = recipient or auth.uid() = sender)
    with check (auth.uid() = recipient or auth.uid() = sender);

-- A message flood in the free tier is the one thing that could burn the quota, so the database
-- itself refuses more than 20 messages a minute from one person.
create or replace function public.messages_rate_limit() returns trigger
language plpgsql security definer set search_path = public as 'begin
    if (select count(*) from public.messages
        where sender = new.sender and created_at > now() - interval ''1 minute'') >= 20 then
        raise exception ''Slow down a little: 20 messages a minute.'';
    end if;
    return new;
end ';

drop trigger if exists messages_rate_limit on public.messages;
create trigger messages_rate_limit before insert on public.messages
    for each row execute function public.messages_rate_limit();

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

drop policy if exists parties_read on public.parties;
create policy parties_read on public.parties
    for select to authenticated using (
        host = auth.uid()
        or exists (select 1 from public.party_members m where m.party_id = parties.id and m.member = auth.uid())
    );

drop policy if exists parties_insert on public.parties;
create policy parties_insert on public.parties
    for insert to authenticated with check (auth.uid() = host);

drop policy if exists parties_update on public.parties;
create policy parties_update on public.parties
    for update to authenticated using (auth.uid() = host);

drop policy if exists parties_delete on public.parties;
create policy parties_delete on public.parties
    for delete to authenticated using (auth.uid() = host);

drop policy if exists party_members_read on public.party_members;
create policy party_members_read on public.party_members
    for select to authenticated using (true);

drop policy if exists party_members_insert on public.party_members;
create policy party_members_insert on public.party_members
    for insert to authenticated with check (auth.uid() = member);

drop policy if exists party_members_delete on public.party_members;
create policy party_members_delete on public.party_members
    for delete to authenticated using (auth.uid() = member);

-- ---- changing your own username ----------------------------------------------------------
-- One change a month, enforced by the database rather than by the app, so re-installing the APK
-- does not reset the clock. Returns null on success, or the sentence to show the player.

create or replace function public.claim_username(p_username text) returns text
language plpgsql security definer set search_path = public as 'declare
    me uuid := auth.uid();
    current_username text;
    changed timestamptz;
begin
    if me is null then
        return ''Not signed in.'';
    end if;
    if p_username is null or p_username !~ ''^[A-Za-z0-9_]{3,20}$'' then
        return ''Use 3-20 letters, numbers or _ .'';
    end if;
    if p_username !~ ''[A-Za-z]'' or p_username !~ ''[0-9]'' then
        return ''The username needs at least one letter and one number.'';
    end if;
    select username, username_changed_at into current_username, changed
        from public.profiles where id = me;
    if changed is not null
        and lower(coalesce(current_username, '''')) <> lower(p_username)
        and changed > now() - interval ''30 days'' then
        return ''Usernames change once a month. Next change: ''
            || to_char(changed + interval ''30 days'', ''DD Mon YYYY'') || ''.'';
    end if;
    if exists (select 1 from public.profiles where lower(username) = lower(p_username) and id <> me) then
        return ''That username is taken.'';
    end if;
    update public.profiles
        set username = p_username,
            username_changed_at = case
                when lower(coalesce(current_username, '''')) = lower(p_username) then username_changed_at
                else now()
            end
        where id = me;
    return null;
end ';

-- ---- finding someone by their exact username ---------------------------------------------
-- This is the only door to a stranger's profile: an exact, case-insensitive username. There is
-- no search-by-prefix and no list-all, which is what keeps people out of a directory.

create or replace function public.find_player(p_username text) returns jsonb
language plpgsql security definer set search_path = public as 'declare
    player public.profiles;
begin
    if p_username is null or char_length(p_username) < 3 then
        return null;
    end if;
    select * into player from public.profiles
        where lower(username) = lower(p_username) limit 1;
    if player.id is null then
        return null;
    end if;
    return jsonb_build_object(
        ''id'', player.id,
        ''username'', player.username,
        ''name'', player.name,
        ''mc_username'', player.mc_username,
        ''avatar_url'', player.avatar_url,
        ''country'', player.country,
        ''last_seen_ms'', (extract(epoch from player.last_seen) * 1000)::bigint,
        ''online'', player.last_seen > now() - interval ''2 minutes''
    );
end ';

-- ---- the profile card: fields, counts and ranks in one round trip ------------------------

create or replace function public.profile_card(p_username text) returns jsonb
language plpgsql security definer set search_path = public as 'declare
    p public.profiles;
    friends_count int;
    followers_count int;
    following_count int;
    messages_count int;
    ranks jsonb := ''[]''::jsonb;
    complete boolean;
begin
    select * into p from public.profiles
        where (p_username is not null and lower(username) = lower(p_username))
           or (p_username is null and id = auth.uid())
        limit 1;
    if p.id is null then
        return null;
    end if;

    select count(*) into friends_count from public.friendships
        where status = ''accepted'' and (requester = p.id or addressee = p.id);
    select count(*) into followers_count from public.follows where following = p.id;
    select count(*) into following_count from public.follows where follower = p.id;
    select count(*) into messages_count from public.messages where sender = p.id;

    complete := p.avatar_url is not null and p.banner_url is not null
        and p.bio is not null and p.country is not null;

    if (select count(*) from public.profiles where created_at <= p.created_at) <= 100 then
        ranks := ranks || ''"early"''::jsonb;
    end if;
    if complete then ranks := ranks || ''"complete"''::jsonb; end if;
    if messages_count >= 100 then ranks := ranks || ''"chatterbox"''::jsonb; end if;
    if friends_count >= 10 then ranks := ranks || ''"butterfly"''::jsonb; end if;
    if followers_count >= 25 then ranks := ranks || ''"trendsetter"''::jsonb; end if;
    if p.created_at < now() - interval ''180 days'' then ranks := ranks || ''"veteran"''::jsonb; end if;
    if p.looking_to_play then ranks := ranks || ''"ready"''::jsonb; end if;

    ranks := ranks || coalesce(
        (select jsonb_agg(to_jsonb(rank_key)) from public.rank_grants where user_id = p.id),
        ''[]''::jsonb);

    return jsonb_build_object(
        ''id'', p.id,
        ''username'', p.username,
        ''name'', p.name,
        ''mc_username'', p.mc_username,
        ''country'', p.country,
        ''bio'', p.bio,
        ''avatar_url'', p.avatar_url,
        ''banner_url'', p.banner_url,
        ''skin_url'', p.skin_url,
        ''cape_url'', p.cape_url,
        ''status_text'', p.status_text,
        ''looking_to_play'', p.looking_to_play,
        ''onboarded'', p.onboarded,
        ''username_changed_at'', p.username_changed_at,
        ''last_seen_ms'', (extract(epoch from p.last_seen) * 1000)::bigint,
        ''followers'', followers_count,
        ''following'', following_count,
        ''friends'', friends_count,
        ''messages'', messages_count,
        ''ranks'', ranks,
        ''is_me'', p.id = auth.uid(),
        ''i_follow'', exists (select 1 from public.follows where follower = auth.uid() and following = p.id)
    );
end ';

-- ---- presence with a little more to say --------------------------------------------------

create or replace function public.set_presence(p_status text, p_looking boolean) returns void
language sql security definer set search_path = public as '    update public.profiles
        set last_seen = now(),
            status_text = nullif(left(coalesce(p_status, ''''), 60), ''''),
            looking_to_play = coalesce(p_looking, false)
        where id = auth.uid();';

-- ---- image storage -----------------------------------------------------------------------

insert into storage.buckets (id, name, public)
values ('social-media', 'social-media', true)
on conflict (id) do update set public = true;

drop policy if exists social_media_read on storage.objects;
create policy social_media_read on storage.objects
    for select using (bucket_id = 'social-media');

drop policy if exists social_media_insert on storage.objects;
create policy social_media_insert on storage.objects
    for insert to authenticated with check (
        bucket_id = 'social-media' and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists social_media_update on storage.objects;
create policy social_media_update on storage.objects
    for update to authenticated using (
        bucket_id = 'social-media' and (storage.foldername(name))[1] = auth.uid()::text
    );

drop policy if exists social_media_delete on storage.objects;
create policy social_media_delete on storage.objects
    for delete to authenticated using (
        bucket_id = 'social-media' and (storage.foldername(name))[1] = auth.uid()::text
    );

-- ---- invite codes ------------------------------------------------------------------------
-- Every profile has a short code: the first eight characters of its id, no dashes. Sharing it is
-- the second (and last) way to reach a stranger; it is derived, not stored, so nothing to mint.

create or replace function public.find_by_code(p_code text) returns jsonb
language plpgsql security definer set search_path = public as 'declare
    player public.profiles;
    clean text := upper(regexp_replace(coalesce(p_code, ''''), ''[^a-zA-Z0-9]'', '''', ''g''));
begin
    if char_length(clean) < 6 then
        return null;
    end if;
    select * into player from public.profiles
        where upper(left(replace(id::text, ''-'', ''''), 8)) = left(clean, 8)
        limit 1;
    if player.id is null then
        return null;
    end if;
    return jsonb_build_object(
        ''id'', player.id,
        ''username'', player.username,
        ''name'', player.name,
        ''mc_username'', player.mc_username,
        ''avatar_url'', player.avatar_url,
        ''country'', player.country,
        ''last_seen_ms'', (extract(epoch from player.last_seen) * 1000)::bigint,
        ''online'', player.last_seen > now() - interval ''2 minutes''
    );
end ';
