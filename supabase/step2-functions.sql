-- Aerix social layer, step 2 of 3 - functions.
--
-- Paste ONE file at a time into Supabase -> SQL Editor -> New query -> Run, then move on to the
-- next one. Each file on its own is small enough to copy on a phone without cutting a statement
-- in half, and every statement is idempotent, so running a step twice changes nothing.

-- The lookup and counting functions. Plain quoted bodies on purpose: a mobile paste cannot
-- break them the way a $$ body can.

-- ---- retention ---------------------------------------------------------------------------
-- Called by the app (or a nightly job) to keep the table small on the free tier.

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
                coalesce(pr.username, pr.mc_username, pr.display_name, ''Player'') as host_name,
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
