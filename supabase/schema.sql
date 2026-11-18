-- MsgU Supabase private direct messages • run once in Supabase SQL Editor.
-- No service-role credentials belong in an Android app.
create extension if not exists pgcrypto;

create table if not exists public.profiles (
 id uuid primary key references auth.users(id) on delete cascade,
 username text not null unique check (username ~ '^[a-z0-9_]{3,20}$'),
 display_name text not null check (char_length(trim(display_name)) between 2 and 50),
 discoverable boolean not null default true,
 created_at timestamptz not null default now()
);
create table if not exists public.conversations (
 id uuid primary key default gen_random_uuid(),
 user_low uuid not null references public.profiles(id),
 user_high uuid not null references public.profiles(id),
 requested_by uuid not null references public.profiles(id),
 status text not null default 'pending' check (status in ('pending','accepted','declined')),
 last_preview text not null default '',
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(user_low,user_high),
 check (user_low < user_high),
 check (requested_by in (user_low,user_high))
);
create index if not exists conversations_low_recent on public.conversations(user_low,updated_at desc);
create index if not exists conversations_high_recent on public.conversations(user_high,updated_at desc);

create table if not exists public.blocks (
 blocker uuid not null references public.profiles(id) on delete cascade,
 blocked uuid not null references public.profiles(id) on delete cascade,
 created_at timestamptz not null default now(),
 primary key (blocker,blocked),
 check (blocker <> blocked)
);
create index if not exists blocks_by_blocked on public.blocks(blocked,blocker);

create table if not exists public.messages (
 id uuid primary key default gen_random_uuid(),
 conversation_id uuid not null references public.conversations(id) on delete cascade,
 sender_id uuid not null references public.profiles(id),
 body text not null check (char_length(trim(body)) between 1 and 3000),
 client_nonce uuid not null,
 created_at timestamptz not null default now(),
 unique(conversation_id,sender_id,client_nonce)
);
create index if not exists messages_recent on public.messages(conversation_id,created_at desc,id desc);

alter table public.profiles enable row level security;
alter table public.conversations enable row level security;
alter table public.blocks enable row level security;
alter table public.messages enable row level security;

-- Friend visibility: only discoverable profiles, one's own, or peers with an existing conversation.
create policy "profile read allowed" on public.profiles for select to authenticated using (
 id = (select auth.uid()) or discoverable or exists (
   select 1 from public.conversations c
   where c.user_low = (select auth.uid()) and c.user_high = profiles.id
      or c.user_high = (select auth.uid()) and c.user_low = profiles.id
 )
);
create policy "profile self create" on public.profiles for insert to authenticated
 with check (id = (select auth.uid()));
create policy "profile self update" on public.profiles for update to authenticated
 using (id = (select auth.uid())) with check (id = (select auth.uid()));

create policy "conversations visible to participants" on public.conversations for select to authenticated
 using ((select auth.uid()) in (user_low,user_high));
-- All creation goes through audited RPC, never from arbitrary direct client writes.
create policy "participant accepts or declines" on public.conversations for update to authenticated
 using ((select auth.uid()) in (user_low,user_high)
     and requested_by <> (select auth.uid()) and status = 'pending')
 with check ((select auth.uid()) in (user_low,user_high)
     and requested_by <> (select auth.uid()) and status in ('accepted','declined'));

create policy "read own blocks" on public.blocks for select to authenticated
 using (blocker = (select auth.uid()));
create policy "create own block" on public.blocks for insert to authenticated
 with check (blocker = (select auth.uid()));
create policy "delete own block" on public.blocks for delete to authenticated
 using (blocker = (select auth.uid()));

create policy "read accepted messages as participant" on public.messages for select to authenticated
 using (exists (
   select 1 from public.conversations c where c.id = conversation_id
   and c.status = 'accepted' and (select auth.uid()) in (c.user_low,c.user_high)
 ));
create policy "send only in accepted nonblocked conversation" on public.messages for insert to authenticated
 with check (
 sender_id = (select auth.uid())
 and exists (
  select 1 from public.conversations c
  where c.id = conversation_id and c.status = 'accepted'
  and (select auth.uid()) in (c.user_low,c.user_high)
  and not exists (
   select 1 from public.blocks b
   where (b.blocker=c.user_low and b.blocked=c.user_high)
      or (b.blocker=c.user_high and b.blocked=c.user_low)
  )
 )
);

-- Prevent clients from changing ownership, participants, statuses arbitrarily or using UPDATE on messages.
revoke all on public.profiles, public.conversations, public.messages, public.blocks from anon;
grant select,insert on public.profiles to authenticated;
grant update (username,display_name,discoverable) on public.profiles to authenticated;
grant select on public.conversations to authenticated;
grant update (status) on public.conversations to authenticated;
grant select,insert on public.messages to authenticated;
grant select,insert,delete on public.blocks to authenticated;

-- RPC invokes securely on server, preventing a forged invite to non-discoverable users.
create or replace function public.start_chat(peer_username text)
returns uuid language plpgsql security definer set search_path = public, pg_temp as $$
declare
 me uuid := auth.uid();
 peer uuid;
 low_id uuid;
 high_id uuid;
 new_chat uuid;
begin
 if me is null then raise exception 'Sign in first'; end if;
 if peer_username !~ '^[a-z0-9_]{3,20}$' then raise exception 'Invalid username'; end if;
 select id into peer from public.profiles
 where username = lower(peer_username) and discoverable = true;
 if peer is null or peer = me then raise exception 'Username unavailable'; end if;
 if not exists(select 1 from public.profiles where id = me) then
   raise exception 'Set up your profile first';
 end if;
 if exists (select 1 from public.blocks b
    where (b.blocker = me and b.blocked = peer)
       or (b.blocker = peer and b.blocked = me)) then
   raise exception 'Chat unavailable';
 end if;
 low_id := least(me,peer); high_id := greatest(me,peer);
 insert into public.conversations(user_low,user_high,requested_by,status)
 values(low_id,high_id,me,'pending') on conflict(user_low,user_high) do nothing;
 select id into new_chat from public.conversations where user_low=low_id and user_high=high_id;
 return new_chat;
end $$;
revoke all on function public.start_chat(text) from public,anon;
grant execute on function public.start_chat(text) to authenticated;

create or replace function public.touch_chat_on_message()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
 update public.conversations set updated_at=NEW.created_at, last_preview=left(NEW.body,96)
 where id=NEW.conversation_id and updated_at <= NEW.created_at;
 return NEW;
end $$;
drop trigger if exists messages_touch_chat on public.messages;
create trigger messages_touch_chat after insert on public.messages
 for each row execute function public.touch_chat_on_message();

-- Realtime supports immediate updates, but clients also poll while visible as a fallback.
do $$ begin
 if not exists (select 1 from pg_publication_tables
    where pubname='supabase_realtime' and schemaname='public' and tablename='messages') then
    alter publication supabase_realtime add table public.messages;
 end if;
end $$;
