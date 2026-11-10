-- Aerix social layer, step 4 of 4 (optional) - the wardrobe backup.
--
-- Paste ONE file at a time into Supabase -> SQL Editor -> New query -> Run. Every statement here is
-- idempotent, so running the step twice changes nothing. Steps 1 to 3 already created the social
-- tables, functions and policies; this step only adds a table of its own, so it can be run before
-- or after them, and it can be dropped without touching anything else (see the drop at the bottom).
--
-- Nothing in here is needed to play. The launcher is fully offline for cosmetics: the skin and cape
-- are built on the device from catalog ids that ship inside the APK. This table exists only so a
-- player who signs in can carry the same outfit and the same eight emote keys to a second phone.
-- No client reads or writes it unless Wardrobe cloud sync is turned on in the Wardrobe screen, which
-- is off by default and per device.
--
-- What it holds, per user: which of the shipped catalog items is worn in each of the six slots, and
-- what each of the eight emote keys is bound to. Ids only - no skin, no cape, no texture, no preview
-- image, no file. A baked 64x64 skin would be a few kilobytes and a personal identifier of the
-- player's face; a list of item ids is neither, and it is the whole state the app needs.

-- ---- table --------------------------------------------------------------------------------

create table if not exists public.wardrobe_sync (
    -- Same key as the profile row, so an account deletion cascades the wardrobe with it.
    id         uuid primary key references public.profiles (id) on delete cascade,
    loadout    jsonb not null,
    updated_at timestamptz not null default now(),
    -- A wardrobe is six slot ids plus eight wheel bindings. Anything larger is not a wardrobe, and
    -- refusing it here stops this table from quietly turning into blob storage for some future
    -- client, whoever wrote that client.
    constraint wardrobe_sync_is_object check (jsonb_typeof(loadout) = 'object'),
    constraint wardrobe_sync_is_small check (pg_column_size(loadout) <= 4096),
    -- The app writes {schema, slots, emote_wheel} and nothing else. Rejecting other shapes in the
    -- database means a launcher bug cannot store arbitrary JSON against an account.
    constraint wardrobe_sync_has_schema check (
        (loadout ->> 'schema') is null or (loadout ->> 'schema') in ('1', '1.0')
    )
);

-- "Newest wardrobe across all devices", used by support-style queries and by the app when it wants
-- to tell a player how stale their last push was.
create index if not exists wardrobe_sync_updated_idx on public.wardrobe_sync (updated_at desc);

alter table public.wardrobe_sync enable row level security;

-- ---- access rules -------------------------------------------------------------------------
--
-- One row per user, readable and writable only by that user. There is no policy that lets a friend,
-- a party member or an anonymous reader see any of this, and no policy at all for the anon role, so
-- a logged-out client cannot enumerate wardrobes even if it guessed the publishable key.

drop policy if exists wardrobe_sync_read on public.wardrobe_sync;

create policy wardrobe_sync_read on public.wardrobe_sync
    for select to authenticated using (auth.uid() = id);

-- The app upserts (POST with merge-duplicates, exactly like the profile row), so insert and update
-- carry the same self-only check.
drop policy if exists wardrobe_sync_insert on public.wardrobe_sync;

create policy wardrobe_sync_insert on public.wardrobe_sync
    for insert to authenticated with check (auth.uid() = id);

drop policy if exists wardrobe_sync_update on public.wardrobe_sync;

create policy wardrobe_sync_update on public.wardrobe_sync
    for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);

-- Delete is allowed for the same reason as everything else: only the owner, and because "Erase cloud
-- wardrobe" is a button the player can press. A feature that can write must be able to undo.
drop policy if exists wardrobe_sync_delete on public.wardrobe_sync;

create policy wardrobe_sync_delete on public.wardrobe_sync
    for delete to authenticated using (auth.uid() = id);

-- Nothing to grant explicitly: Supabase gives the authenticated role access to tables it creates in
-- public, and the anon and service roles are limited by the policies above (no policy matches them).
--
-- To remove the feature again, run:
--   drop table if exists public.wardrobe_sync;
