-- Aerix Launcher social layer — schema for the free Supabase tier.
--
-- Paste this whole file into Supabase -> SQL Editor -> New query -> Run, once.
-- It is idempotent: running it again does not break anything.
--
-- What it holds: profiles, friendships, direct messages, parties & party chat, LFG board,
-- friends' shared servers, blocks/reports, and 30-day downscaled chat media cleanup.

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

alter table public.profiles add column if not exists role_badge text;

alter table public.profiles add column if not exists presence_mode text not null default 'online';

-- ---- verified email-bound role badges ((OWNER) & (CO-OWNER)) -----------------------------
-- Enforced server-side from auth.users.email so non-staff accounts can never spoof tags.

create or replace function public.enforce_profile_role_badge() returns trigger
language plpgsql security definer set search_path = public as 'declare
    auth_email text;
    clean_name text;
    clean_display text;
    fallback_base text;
begin
    select lower(trim(email)) into auth_email from auth.users where id = new.id;
    clean_name := nullif(trim(regexp_replace(coalesce(new.name, ''''), ''\s*\((CO[-_\s]?OWNER|OWNER)\)\s*'', '' '', ''gi'')), '''');
    clean_display := nullif(trim(regexp_replace(coalesce(new.display_name, ''''), ''\s*\((CO[-_\s]?OWNER|OWNER)\)\s*'', '' '', ''gi'')), '''');
    fallback_base := coalesce(clean_name, clean_display, new.username, new.mc_username, ''Player'');

    if auth_email = ''entitybrian69@gmail.com'' then
        new.role_badge := ''OWNER'';
        new.name := fallback_base || '' (OWNER)'';
        new.display_name := fallback_base || '' (OWNER)'';
    elsif auth_email = ''kushaladhikari982648@gmail.com'' then
        new.role_badge := ''CO-OWNER'';
        new.name := fallback_base || '' (CO-OWNER)'';
        new.display_name := fallback_base || '' (CO-OWNER)'';
    else
        new.role_badge := null;
        new.name := clean_name;
        new.display_name := clean_display;
    end if;
    return new;
end ';

drop trigger if exists profiles_enforce_role_badge on public.profiles;

create trigger profiles_enforce_role_badge
    before insert or update on public.profiles
    for each row execute function public.enforce_profile_role_badge();

-- Backfill any existing profile rows so verified staff badges take effect immediately on SQL run.
update public.profiles set display_name = display_name where true;

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

-- ---- party extras, party chat, shared servers and realtime -------------------------------

alter table public.parties add column if not exists max_members int not null default 4;

alter table public.parties add column if not exists loader text;

alter table public.parties add column if not exists modpack_source text;

alter table public.parties add column if not exists modpack_id text;

alter table public.parties add column if not exists modpack_icon_url text;

alter table public.parties add column if not exists server_address text;

alter table public.parties add column if not exists is_public boolean not null default false;

create table if not exists public.party_messages (
    id              bigint generated always as identity primary key,
    party_id        uuid not null references public.parties (id) on delete cascade,
    sender          uuid not null references public.profiles (id) on delete cascade,
    body            text not null check (char_length(body) between 1 and 2000),
    reply_to        bigint references public.party_messages (id) on delete set null,
    attachment_url  text,
    edited_at       timestamptz,
    created_at      timestamptz not null default now()
);

create index if not exists party_messages_party_idx on public.party_messages (party_id, created_at asc);

alter table public.party_messages enable row level security;

create table if not exists public.server_shares (
    id              bigint generated always as identity primary key,
    owner           uuid not null references public.profiles (id) on delete cascade,
    name            text not null check (char_length(name) between 1 and 80),
    address         text not null check (char_length(address) between 3 and 255),
    mc_version      text,
    loader          text,
    description     text,
    created_at      timestamptz not null default now()
);

create index if not exists server_shares_owner_idx on public.server_shares (owner, created_at desc);

alter table public.server_shares enable row level security;

-- ---- image storage -----------------------------------------------------------------------

insert into storage.buckets (id, name, public)
values ('social-media', 'social-media', true)
on conflict (id) do update set public = true;

create or replace function public.prune_messages() returns void
language sql security definer as '    delete from public.messages where created_at < now() - interval ''90 days'';';

-- ---- presence helper ---------------------------------------------------------------------
-- Keeps profiles.last_seen current without giving the app write access to other people's rows.

create or replace function public.touch_presence() returns void
language sql security definer as '    update public.profiles set last_seen = now() where id = auth.uid();';

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
        ''role_badge'', player.role_badge,
        ''mc_username'', player.mc_username,
        ''avatar_url'', player.avatar_url,
        ''country'', player.country,
        ''last_seen_ms'', (extract(epoch from player.last_seen) * 1000)::bigint,
        ''online'', player.last_seen > now() - interval ''2 minutes''
    );
end ';

-- ---- the profile card: fields, counts and ranks in one round trip ------------------------

drop function if exists public.profile_card();

create or replace function public.profile_card(p_username text default null) returns jsonb
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
        ''role_badge'', p.role_badge,
        ''presence_mode'', p.presence_mode,
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
        ''role_badge'', player.role_badge,
        ''mc_username'', player.mc_username,
        ''avatar_url'', player.avatar_url,
        ''country'', player.country,
        ''last_seen_ms'', (extract(epoch from player.last_seen) * 1000)::bigint,
        ''online'', player.last_seen > now() - interval ''2 minutes''
    );
end ';

-- ---- 30-day chat attachment cleanup & auto-expire party cleanup -------------------------

create or replace function public.prune_chat_media() returns void
language sql security definer set search_path = public as '    update public.messages
        set attachment_url = null
        where attachment_url is not null and created_at < now() - interval ''30 days'';
    update public.party_messages
        set attachment_url = null
        where attachment_url is not null and created_at < now() - interval ''30 days'';';

create or replace function public.prune_expired_parties() returns void
language sql security definer set search_path = public as '    delete from public.parties where expires_at <= now();';

-- ---- mutual friends on a pending request -------------------------------------------------

create or replace function public.mutual_friends(p_other uuid) returns jsonb
language plpgsql security definer set search_path = public as 'declare
    me uuid := auth.uid();
    mutual_ids uuid[];
    names jsonb := ''[]''::jsonb;
begin
    if me is null or p_other is null then
        return jsonb_build_object(''count'', 0, ''names'', ''[]''::jsonb);
    end if;
    select array_agg(x.fid) into mutual_ids from (
        select case when f1.requester = me then f1.addressee else f1.requester end as fid
        from public.friendships f1
        where f1.status = ''accepted'' and (f1.requester = me or f1.addressee = me)
        intersect
        select case when f2.requester = p_other then f2.addressee else f2.requester end as fid
        from public.friendships f2
        where f2.status = ''accepted'' and (f2.requester = p_other or f2.addressee = p_other)
    ) x;
    if mutual_ids is null or array_length(mutual_ids, 1) is null then
        return jsonb_build_object(''count'', 0, ''names'', ''[]''::jsonb);
    end if;
    select coalesce(jsonb_agg(coalesce(username, mc_username, display_name, ''Player'')), ''[]''::jsonb)
        into names
        from public.profiles
        where id = any(mutual_ids);
    return jsonb_build_object(''count'', coalesce(array_length(mutual_ids, 1), 0), ''names'', names);
end ';

-- ---- party host transfer and member kick -------------------------------------------------

create or replace function public.transfer_party_host(p_party uuid, p_new_host uuid) returns boolean
language plpgsql security definer set search_path = public as 'declare
    me uuid := auth.uid();
begin
    if me is null or p_party is null or p_new_host is null then
        return false;
    end if;
    if not exists (select 1 from public.parties where id = p_party and host = me) then
        return false;
    end if;
    insert into public.party_members (party_id, member)
        values (p_party, p_new_host)
        on conflict (party_id, member) do nothing;
    update public.parties set host = p_new_host where id = p_party and host = me;
    return true;
end ';

create or replace function public.kick_party_member(p_party uuid, p_member uuid) returns boolean
language plpgsql security definer set search_path = public as 'declare
    me uuid := auth.uid();
begin
    if me is null or p_party is null or p_member is null or p_member = me then
        return false;
    end if;
    if not exists (select 1 from public.parties where id = p_party and host = me) then
        return false;
    end if;
    delete from public.party_members where party_id = p_party and member = p_member;
    return true;
end ';

-- ---- public Looking-for-Group board ------------------------------------------------------

create or replace function public.list_lfg_parties(
    p_mc_version text default null,
    p_loader text default null
) returns jsonb
language plpgsql security definer set search_path = public as 'begin
    perform public.prune_expired_parties();
    return coalesce((
        select jsonb_agg(row_to_json(q)) from (
            select
                pa.id,
                pa.host,
                coalesce(pr.name, pr.username, pr.mc_username, pr.display_name, ''Player'') as host_name,
                pr.role_badge as host_role,
                pr.avatar_url as host_avatar,
                pa.title,
                pa.instance,
                pa.version,
                pa.mc_version,
                pa.loader,
                pa.modpack_source,
                pa.modpack_id,
                pa.modpack_icon_url,
                pa.server_address,
                pa.max_members,
                (select count(*) from public.party_members pm where pm.party_id = pa.id)::int as member_count,
                (extract(epoch from pa.expires_at) * 1000)::bigint as expires_at_ms,
                (extract(epoch from pa.created_at) * 1000)::bigint as created_at_ms
            from public.parties pa
            join public.profiles pr on pr.id = pa.host
            where pa.is_public = true
              and pa.expires_at > now()
              and not exists (
                  select 1 from public.blocks b
                  where (b.blocker = auth.uid() and b.blocked = pa.host)
                     or (b.blocker = pa.host and b.blocked = auth.uid())
              )
              and (p_mc_version is null or p_mc_version = '''' or lower(coalesce(pa.mc_version, '''')) = lower(p_mc_version))
              and (p_loader is null or p_loader = '''' or lower(coalesce(pa.loader, '''')) = lower(p_loader))
            order by pa.created_at desc
            limit 50
        ) q
    ), ''[]''::jsonb);
end ';

drop policy if exists profiles_read on public.profiles;

-- A user may only create and change their own profile.
drop policy if exists profiles_insert on public.profiles;

create policy profiles_insert on public.profiles
    for insert to authenticated with check (auth.uid() = id);

drop policy if exists profiles_update on public.profiles;

create policy profiles_update on public.profiles
    for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);

-- A user sees the requests they sent and the ones they received, nothing else.
drop policy if exists friendships_read on public.friendships;

create policy friendships_read on public.friendships
    for select to authenticated using (auth.uid() = requester or auth.uid() = addressee);

-- Only the requester creates a request, and always as 'pending', unless either side blocked the other.
drop policy if exists friendships_insert on public.friendships;

create policy friendships_insert on public.friendships
    for insert to authenticated with check (
        auth.uid() = requester
        and status = 'pending'
        and not exists (
            select 1 from public.blocks b
            where (b.blocker = addressee and b.blocked = auth.uid())
               or (b.blocker = auth.uid() and b.blocked = addressee)
        )
    );

-- Either side may answer it (accept, decline), and either may remove the friendship.
drop policy if exists friendships_update on public.friendships;

create policy friendships_update on public.friendships
    for update to authenticated using (auth.uid() = requester or auth.uid() = addressee);

drop policy if exists friendships_delete on public.friendships;

create policy friendships_delete on public.friendships
    for delete to authenticated using (auth.uid() = requester or auth.uid() = addressee);

-- Only the two people in a conversation can read it, and blocked pairs stop seeing new traffic.
drop policy if exists messages_read on public.messages;

create policy messages_read on public.messages
    for select to authenticated using (
        (auth.uid() = sender or auth.uid() = recipient)
        and not exists (
            select 1 from public.blocks b
            where (b.blocker = auth.uid() and (b.blocked = sender or b.blocked = recipient))
        )
    );

-- You may only send as yourself, and server-side blocks forbid messaging in either direction.
drop policy if exists messages_insert on public.messages;

create policy messages_insert on public.messages
    for insert to authenticated with check (
        auth.uid() = sender
        and not exists (
            select 1 from public.blocks b
            where (b.blocker = recipient and b.blocked = auth.uid())
               or (b.blocker = auth.uid() and b.blocked = recipient)
        )
    );

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

drop policy if exists follows_read on public.follows;

create policy follows_read on public.follows
    for select to authenticated using (follower = auth.uid() or following = auth.uid());

drop policy if exists follows_insert on public.follows;

create policy follows_insert on public.follows
    for insert to authenticated with check (auth.uid() = follower);

drop policy if exists follows_delete on public.follows;

create policy follows_delete on public.follows
    for delete to authenticated using (auth.uid() = follower);

drop policy if exists blocks_read on public.blocks;

create policy blocks_read on public.blocks
    for select to authenticated using (auth.uid() = blocker);

drop policy if exists blocks_write on public.blocks;

create policy blocks_write on public.blocks
    for insert to authenticated with check (auth.uid() = blocker);

drop policy if exists blocks_delete on public.blocks;

create policy blocks_delete on public.blocks
    for delete to authenticated using (auth.uid() = blocker);

drop policy if exists reports_insert on public.reports;

create policy reports_insert on public.reports
    for insert to authenticated with check (auth.uid() = reporter);

drop policy if exists rank_grants_read on public.rank_grants;

create policy rank_grants_read on public.rank_grants
    for select to authenticated using (auth.uid() = user_id);

drop policy if exists messages_update on public.messages;

create policy messages_update on public.messages
    for update to authenticated
    using (auth.uid() = recipient or auth.uid() = sender)
    with check (auth.uid() = recipient or auth.uid() = sender);

drop trigger if exists messages_rate_limit on public.messages;

create trigger messages_rate_limit before insert on public.messages
    for each row execute function public.messages_rate_limit();

drop policy if exists parties_read on public.parties;

create policy parties_read on public.parties
    for select to authenticated using (
        host = auth.uid()
        or (is_public = true and expires_at > now())
        or exists (select 1 from public.party_members m where m.party_id = parties.id and m.member = auth.uid())
    );

drop policy if exists parties_insert on public.parties;

create policy parties_insert on public.parties
    for insert to authenticated with check (auth.uid() = host);

drop policy if exists parties_update on public.parties;

create policy parties_update on public.parties
    for update to authenticated using (auth.uid() = host) with check (true);

drop policy if exists parties_delete on public.parties;

create policy parties_delete on public.parties
    for delete to authenticated using (auth.uid() = host);

drop policy if exists party_members_read on public.party_members;

create policy party_members_read on public.party_members
    for select to authenticated using (
        member = auth.uid()
        or exists (select 1 from public.parties p where p.id = party_members.party_id and (p.host = auth.uid() or p.is_public = true))
        or exists (select 1 from public.party_members self where self.party_id = party_members.party_id and self.member = auth.uid())
    );

drop policy if exists party_members_insert on public.party_members;

create policy party_members_insert on public.party_members
    for insert to authenticated with check (
        auth.uid() = member
        and (
            select count(*) from public.party_members pm where pm.party_id = party_members.party_id
        ) < coalesce((select max_members from public.parties p where p.id = party_members.party_id), 16)
    );

drop policy if exists party_members_delete on public.party_members;

create policy party_members_delete on public.party_members
    for delete to authenticated using (
        auth.uid() = member
        or exists (select 1 from public.parties p where p.id = party_members.party_id and p.host = auth.uid())
    );

drop policy if exists party_messages_read on public.party_messages;

create policy party_messages_read on public.party_messages
    for select to authenticated using (
        exists (select 1 from public.parties p where p.id = party_messages.party_id and p.host = auth.uid())
        or exists (select 1 from public.party_members pm where pm.party_id = party_messages.party_id and pm.member = auth.uid())
    );

drop policy if exists party_messages_insert on public.party_messages;

create policy party_messages_insert on public.party_messages
    for insert to authenticated with check (
        auth.uid() = sender
        and (
            exists (select 1 from public.parties p where p.id = party_messages.party_id and p.host = auth.uid())
            or exists (select 1 from public.party_members pm where pm.party_id = party_messages.party_id and pm.member = auth.uid())
        )
    );

drop policy if exists party_messages_update on public.party_messages;

create policy party_messages_update on public.party_messages
    for update to authenticated using (auth.uid() = sender) with check (auth.uid() = sender);

drop policy if exists server_shares_read on public.server_shares;

create policy server_shares_read on public.server_shares
    for select to authenticated using (
        owner = auth.uid()
        or exists (
            select 1 from public.friendships f
            where f.status = 'accepted'
              and ((f.requester = auth.uid() and f.addressee = server_shares.owner)
                or (f.addressee = auth.uid() and f.requester = server_shares.owner))
        )
    );

drop policy if exists server_shares_insert on public.server_shares;

create policy server_shares_insert on public.server_shares
    for insert to authenticated with check (auth.uid() = owner);

drop policy if exists server_shares_delete on public.server_shares;

create policy server_shares_delete on public.server_shares
    for delete to authenticated using (auth.uid() = owner);

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

-- ---- Supabase Realtime publication (idempotent) ------------------------------------------

do 'declare
    tbl text;
begin
    if exists (select 1 from pg_publication where pubname = ''supabase_realtime'') then
        foreach tbl in array array[
            ''profiles'', ''friendships'', ''messages'',
            ''parties'', ''party_members'', ''party_messages'', ''server_shares''
        ] loop
            if not exists (
                select 1 from pg_publication_tables
                where pubname = ''supabase_realtime'' and schemaname = ''public'' and tablename = tbl
            ) then
                execute format(''alter publication supabase_realtime add table public.%I'', tbl);
            end if;
        end loop;
    end if;
end ';
