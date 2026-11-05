-- Authenticated game sessions for the existing opt-in Aerix game-name directory.
-- These credentials are separate from the Social bearer/refresh tokens.
create table if not exists public.aerix_game_sessions (
    token_hash text primary key,
    owner_id uuid not null references auth.users(id) on delete cascade,
    expires_at timestamptz not null
);
create index if not exists aerix_game_sessions_owner on public.aerix_game_sessions(owner_id);
create table if not exists public.aerix_game_joins (
    owner_id uuid not null references auth.users(id) on delete cascade,
    server_id text not null,
    expires_at timestamptz not null,
    primary key(owner_id, server_id)
);
alter table public.aerix_game_sessions enable row level security;
alter table public.aerix_game_joins enable row level security;
revoke all on public.aerix_game_sessions from public, anon, authenticated;
revoke all on public.aerix_game_joins from public, anon, authenticated;

create or replace function public.aerix_issue_game_session()
returns table(game_name text, profile_id uuid, access_token text, expires_at_ms bigint)
language plpgsql security definer set search_path = pg_catalog, public, extensions as $$
declare uid uuid := auth.uid(); account public.aerix_game_accounts%rowtype; raw_token text; expiry timestamptz;
begin
    if uid is null then raise exception 'Sign in to Aerix first'; end if;
    select * into account from public.aerix_game_accounts a where a.owner_id = uid for update;
    if not found then raise exception 'Claim a game name first'; end if;
    delete from public.aerix_game_sessions s where s.owner_id = uid and s.expires_at < now();
    delete from public.aerix_game_sessions s where s.owner_id = uid and s.token_hash in (
        select old.token_hash from public.aerix_game_sessions old where old.owner_id = uid
        order by old.expires_at desc offset 7
    );
    raw_token := encode(extensions.gen_random_bytes(32), 'hex');
    expiry := now() + interval '6 hours';
    insert into public.aerix_game_sessions(token_hash, owner_id, expires_at)
    values(encode(extensions.digest(raw_token, 'sha256'), 'hex'), uid, expiry);
    return query select account.game_name, account.offline_uuid, raw_token, (extract(epoch from expiry) * 1000)::bigint;
end $$;
revoke all on function public.aerix_issue_game_session() from public, anon;
grant execute on function public.aerix_issue_game_session() to authenticated;

create or replace function public.aerix_revoke_game_sessions()
returns void language plpgsql security definer set search_path = pg_catalog, public as $$
begin
    if auth.uid() is null then raise exception 'Sign in to Aerix first'; end if;
    perform 1 from public.aerix_game_accounts a where a.owner_id = auth.uid() for update;
    delete from public.aerix_game_sessions s where s.owner_id = auth.uid();
    delete from public.aerix_game_joins j where j.owner_id = auth.uid();
end $$;
revoke all on function public.aerix_revoke_game_sessions() from public, anon;
grant execute on function public.aerix_revoke_game_sessions() to authenticated;

create or replace function public.aerix_game_join(p_token text, p_profile uuid, p_server_id text)
returns boolean language plpgsql security definer set search_path = pg_catalog, public, extensions as $$
declare uid uuid;
begin
    if p_token is null or p_token !~ '^[0-9a-f]{64}$' or p_profile is null or
       p_server_id is null or p_server_id !~ '^-?[0-9a-f]{1,40}$' then return false; end if;
    select s.owner_id into uid from public.aerix_game_sessions s
    join public.aerix_game_accounts a on a.owner_id = s.owner_id
    where s.token_hash = encode(extensions.digest(p_token, 'sha256'), 'hex')
      and s.expires_at > now() and a.offline_uuid = p_profile;
    if uid is null then return false; end if;
    perform 1 from public.aerix_game_accounts a where a.owner_id = uid for update;
    if not exists (select 1 from public.aerix_game_sessions s where s.owner_id = uid
        and s.token_hash = encode(extensions.digest(p_token, 'sha256'), 'hex') and s.expires_at > now()) then return false; end if;
    delete from public.aerix_game_joins j where j.owner_id = uid and j.expires_at < now();
    delete from public.aerix_game_joins j where j.owner_id = uid and j.server_id in (
        select old.server_id from public.aerix_game_joins old where old.owner_id = uid
        order by old.expires_at desc offset 7
    );
    insert into public.aerix_game_joins(owner_id,server_id,expires_at)
    values(uid,p_server_id,now() + interval '60 seconds')
    on conflict (owner_id,server_id) do update set expires_at = excluded.expires_at;
    return true;
end $$;
revoke all on function public.aerix_game_join(text,uuid,text) from public;
grant execute on function public.aerix_game_join(text,uuid,text) to anon, authenticated;

create or replace function public.aerix_game_has_joined(p_name text,p_server_id text)
returns table(game_name text, profile_id uuid, slim boolean, skin_url text, cape_url text)
language plpgsql security definer set search_path = pg_catalog, public as $$
declare uid uuid;
begin
    if p_name is null or p_name !~ '^[A-Za-z0-9_]{3,16}$' or p_server_id is null or
        p_server_id !~ '^-?[0-9a-f]{1,40}$' then return; end if;
    delete from public.aerix_game_joins j using public.aerix_game_accounts a
      where j.owner_id = a.owner_id and a.game_name = p_name and j.server_id = p_server_id
        and j.expires_at > now() returning j.owner_id into uid;
    if uid is null then return; end if;
    return query select a.game_name, a.offline_uuid, a.slim, t.skin_url, t.cape_url
      from public.aerix_game_accounts a
      left join lateral public.aerix_lookup_game_texture(a.offline_uuid) t on true
      where a.owner_id = uid;
end $$;
revoke all on function public.aerix_game_has_joined(text,text) from public;
grant execute on function public.aerix_game_has_joined(text,text) to anon, authenticated;

-- Public profile attributes are limited to shared textures; the game name and UUID are public.
create or replace function public.aerix_game_profile(p_uuid uuid)
returns table(game_name text, profile_id uuid, slim boolean, skin_url text, cape_url text)
language sql stable security definer set search_path = pg_catalog, public as $$
    select a.game_name, a.offline_uuid, a.slim, t.skin_url, t.cape_url
    from public.aerix_game_accounts a
    left join lateral public.aerix_lookup_game_texture(a.offline_uuid) t on true
    where a.offline_uuid = p_uuid
$$;
revoke all on function public.aerix_game_profile(uuid) from public;
grant execute on function public.aerix_game_profile(uuid) to anon, authenticated;

create or replace function public.aerix_game_profile_by_name(p_name text)
returns table(game_name text, profile_id uuid)
language sql stable security definer set search_path = pg_catalog, public as $$
    select a.game_name, a.offline_uuid from public.aerix_game_accounts a
    where p_name ~ '^[A-Za-z0-9_]{3,16}$' and lower(a.game_name) = lower(p_name)
$$;
revoke all on function public.aerix_game_profile_by_name(text) from public;
grant execute on function public.aerix_game_profile_by_name(text) to anon, authenticated;

create or replace function public.aerix_game_validate(p_token text)
returns boolean language sql stable security definer set search_path = pg_catalog, public, extensions as $$
    select p_token ~ '^[0-9a-f]{64}$' and exists (
        select 1 from public.aerix_game_sessions s
        where s.token_hash = encode(extensions.digest(p_token,'sha256'),'hex') and s.expires_at > now()
    )
$$;
revoke all on function public.aerix_game_validate(text) from public;
grant execute on function public.aerix_game_validate(text) to anon, authenticated;

-- Only the Edge Function's service role may read this signing key.
create table if not exists public.aerix_auth_signing_key (
    singleton boolean primary key default true check (singleton),
    private_key text not null,
    public_key text not null
);
alter table public.aerix_auth_signing_key enable row level security;
revoke all on public.aerix_auth_signing_key from public, anon, authenticated;
grant select, insert on public.aerix_auth_signing_key to service_role;

create or replace function public.aerix_game_profile_by_token(p_token text)
returns table(game_name text, profile_id uuid, slim boolean, skin_url text, cape_url text)
language sql stable security definer set search_path = pg_catalog, public, extensions as $$
    select a.game_name, a.offline_uuid, a.slim, t.skin_url, t.cape_url
    from public.aerix_game_sessions s join public.aerix_game_accounts a on a.owner_id=s.owner_id
    left join lateral public.aerix_lookup_game_texture(a.offline_uuid) t on true
    where p_token ~ '^[0-9a-f]{64}$' and s.token_hash=encode(extensions.digest(p_token,'sha256'),'hex')
      and s.expires_at>now()
$$;
revoke all on function public.aerix_game_profile_by_token(text) from public;
grant execute on function public.aerix_game_profile_by_token(text) to anon, authenticated;
