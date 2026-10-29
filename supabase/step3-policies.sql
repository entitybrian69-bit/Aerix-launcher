-- Aerix social layer, step 3 of 3 - access rules.
--
-- Paste ONE file at a time into Supabase -> SQL Editor -> New query -> Run, then move on to the
-- next one. Each file on its own is small enough to copy on a phone without cutting a statement
-- in half, and every statement is idempotent, so running a step twice changes nothing.

-- Row-level security for every table above, plus the storage rules for the social-media bucket.

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

-- Only the two people in a conversation can read it.
drop policy if exists messages_read on public.messages;

create policy messages_read on public.messages
    for select to authenticated using (auth.uid() = sender or auth.uid() = recipient);

-- You may only send as yourself.
drop policy if exists messages_insert on public.messages;

create policy messages_insert on public.messages
    for insert to authenticated with check (auth.uid() = sender);

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
    for select to authenticated using (true);

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
