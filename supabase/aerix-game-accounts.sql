-- Aerix game identities. Existing social profiles and Minecraft account files remain unchanged.
-- A game name is permanent while the social account exists. Public lookup is opt-in.
create table if not exists public.aerix_game_accounts (
    owner_id uuid primary key references auth.users(id) on delete cascade,
    game_name text not null check (game_name ~ '^[A-Za-z0-9_]{3,16}$'),
    offline_uuid uuid not null unique,
    slim boolean not null default false,
    sharing boolean not null default false,
    created_at timestamptz not null default now()
);
create unique index if not exists aerix_game_accounts_name_unique
    on public.aerix_game_accounts (lower(game_name));
alter table public.aerix_game_accounts enable row level security;
revoke all on public.aerix_game_accounts from public, anon, authenticated;

-- UUID.nameUUIDFromBytes("OfflinePlayer:" + game_name) as used by Minecraft offline mode.
create or replace function public.aerix_offline_uuid(p_name text)
returns uuid language sql immutable strict set search_path = pg_catalog as $$
    select (substr(h,1,12) || '3' || substr(h,14,3) ||
        substr('89ab', ((get_byte(decode(h,'hex'),8) >> 4) & 3) + 1, 1) || substr(h,18,15))::uuid
    from (select md5('OfflinePlayer:' || p_name) as h) v
$$;
revoke all on function public.aerix_offline_uuid(text) from public, anon, authenticated;

create or replace function public.aerix_claim_game_name(p_name text)
returns table(game_name text, offline_uuid uuid, slim boolean, sharing boolean)
language plpgsql security definer set search_path = pg_catalog, public as $$
declare uid uuid := auth.uid(); existing public.aerix_game_accounts%rowtype; skin_url text;
begin
    if uid is null then raise exception 'Sign in to Aerix first'; end if;
    if p_name is null or p_name !~ '^[A-Za-z0-9_]{3,16}$' then
        raise exception 'Game names must have 3 to 16 ASCII letters, numbers, or underscores';
    end if;
    select * into existing from public.aerix_game_accounts a where a.owner_id = uid;
    if found then
        if existing.game_name <> p_name then raise exception 'This Aerix account already has a game name'; end if;
    else
        select p.skin_url into skin_url from public.profiles p where p.id = uid;
        insert into public.aerix_game_accounts(owner_id, game_name, offline_uuid, slim)
        values (uid, p_name, public.aerix_offline_uuid(p_name),
            coalesce(skin_url like 'https://lktupxfszfefsoyksfkg.supabase.co/storage/v1/object/public/social-media/' || uid::text || '/game-skin-slim-%', false));
    end if;
    return query select a.game_name, a.offline_uuid, a.slim, a.sharing
        from public.aerix_game_accounts a where a.owner_id = uid;
end $$;
revoke all on function public.aerix_claim_game_name(text) from public, anon;
grant execute on function public.aerix_claim_game_name(text) to authenticated;

create or replace function public.aerix_my_game_account()
returns table(game_name text, offline_uuid uuid, slim boolean, sharing boolean)
language sql stable security definer set search_path = pg_catalog, public as $$
    select a.game_name, a.offline_uuid, a.slim, a.sharing
    from public.aerix_game_accounts a where a.owner_id = auth.uid()
$$;
revoke all on function public.aerix_my_game_account() from public, anon;
grant execute on function public.aerix_my_game_account() to authenticated;

create or replace function public.aerix_set_game_sharing(p_enabled boolean)
returns boolean language plpgsql security definer set search_path = pg_catalog, public as $$
begin
    if auth.uid() is null or p_enabled is null then raise exception 'Sign in to Aerix first'; end if;
    update public.aerix_game_accounts set sharing = p_enabled where owner_id = auth.uid();
    return found;
end $$;
revoke all on function public.aerix_set_game_sharing(boolean) from public, anon;
grant execute on function public.aerix_set_game_sharing(boolean) to authenticated;

create or replace function public.aerix_publish_game_texture(p_kind text, p_url text, p_slim boolean)
returns boolean language plpgsql security definer set search_path = pg_catalog, public as $$
declare uid uuid := auth.uid(); prefix text;
begin
    if uid is null then raise exception 'Sign in to Aerix first'; end if;
    prefix := 'https://lktupxfszfefsoyksfkg.supabase.co/storage/v1/object/public/social-media/' || uid::text || '/';
    if p_kind is null or p_kind not in ('skin','cape') or p_url is null or left(p_url, length(prefix)) <> prefix or
       substr(p_url, length(prefix)+1) !~ '^[A-Za-z0-9_-]+[.]png$' then
        raise exception 'Invalid Aerix texture';
    end if;
    if p_kind = 'skin' then
        update public.profiles set skin_url = p_url where id = uid;
        if not found then return false; end if;
        update public.aerix_game_accounts set slim = coalesce(p_slim, false) where owner_id = uid;
    else
        update public.profiles set cape_url = p_url where id = uid;
        if not found then return false; end if;
    end if;
    return true;
end $$;
revoke all on function public.aerix_publish_game_texture(text,text,boolean) from public, anon;
grant execute on function public.aerix_publish_game_texture(text,text,boolean) to authenticated;

-- Returns only the game-facing information for an explicitly shared UUID.
-- Storage URLs are public and include the owner's user ID; profile fields remain private.
create or replace function public.aerix_lookup_game_texture(p_uuid uuid)
returns table(game_name text, offline_uuid uuid, slim boolean, skin_url text, cape_url text)
language sql stable security definer set search_path = pg_catalog, public as $$
    select a.game_name, a.offline_uuid, a.slim,
        case when p.skin_url like 'https://lktupxfszfefsoyksfkg.supabase.co/storage/v1/object/public/social-media/' || a.owner_id::text || '/%'
          and substr(p.skin_url, length('https://lktupxfszfefsoyksfkg.supabase.co/storage/v1/object/public/social-media/' || a.owner_id::text || '/')+1) ~ '^[A-Za-z0-9_-]+[.]png$' then p.skin_url end,
        case when p.cape_url like 'https://lktupxfszfefsoyksfkg.supabase.co/storage/v1/object/public/social-media/' || a.owner_id::text || '/%'
          and substr(p.cape_url, length('https://lktupxfszfefsoyksfkg.supabase.co/storage/v1/object/public/social-media/' || a.owner_id::text || '/')+1) ~ '^[A-Za-z0-9_-]+[.]png$' then p.cape_url end
    from public.aerix_game_accounts a join public.profiles p on p.id = a.owner_id
    where a.offline_uuid = p_uuid and a.sharing
$$;
revoke all on function public.aerix_lookup_game_texture(uuid) from public;
grant execute on function public.aerix_lookup_game_texture(uuid) to anon, authenticated;

-- The animated Client cape choice is private and independent of the imported cape PNG.
alter table public.aerix_game_accounts add column if not exists client_cape_id text;
create or replace function public.aerix_get_client_cape() returns text language sql stable security definer set search_path = pg_catalog, public as $$
    select a.client_cape_id from public.aerix_game_accounts a where a.owner_id = auth.uid()
$$;
revoke all on function public.aerix_get_client_cape() from public, anon;
grant execute on function public.aerix_get_client_cape() to authenticated;
create or replace function public.aerix_set_client_cape(p_cape_id text) returns boolean language plpgsql security definer set search_path = pg_catalog, public as $$
declare uid uuid := auth.uid(); user_email text;
begin
    if uid is null then raise exception 'Sign in to Aerix first'; end if;
    if p_cape_id is null or p_cape_id not in ('crimson_sovereign','phantom_obsidian','blood_moon','inferno_dragon','crimson_storm','ender_vortex','aurora_drift','cyber_matrix','cosmic_nebula','glacier_shards','sakura_wind','golden_monarch','sculk_pulse','owner_crimson','coowner_celestial') then raise exception 'Unknown cape'; end if;
    if p_cape_id in ('owner_crimson','coowner_celestial') then
        select lower(email) into user_email from auth.users where id = uid and email_confirmed_at is not null;
        if user_email is null or (p_cape_id = 'owner_crimson' and user_email <> 'entitybrian69@gmail.com') or (p_cape_id = 'coowner_celestial' and user_email <> 'kushaladhikari982648@gmail.com') then raise exception 'Cape is locked'; end if;
    end if;
    update public.aerix_game_accounts set client_cape_id = p_cape_id where owner_id = uid;
    return found;
end $$;
revoke all on function public.aerix_set_client_cape(text) from public, anon;
grant execute on function public.aerix_set_client_cape(text) to authenticated;
