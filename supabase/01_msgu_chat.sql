-- MsgU Supabase MVP schema. Run once in the Supabase SQL Editor as project owner.
-- Never use a service_role/secret API key in the Android application.
create extension if not exists pgcrypto;

create table if not exists public.msgu_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique check (username ~ '^[a-z0-9_]{3,20}$'),
  display_name text not null check (char_length(display_name) between 2 and 50),
  discoverable boolean not null default false,
  created_at timestamptz not null default now()
);
create table if not exists public.msgu_chats (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null references auth.users(id) on delete cascade,
  user_b uuid not null references auth.users(id) on delete cascade,
  requested_by uuid not null references auth.users(id),
  status text not null default 'requested' check (status in ('requested','accepted','rejected')),
  created_at timestamptz not null default now(),
  constraint msgu_chat_pair unique (user_a, user_b),
  constraint msgu_pair_order check (user_a < user_b),
  constraint msgu_request_participant check (requested_by in (user_a,user_b))
);
create table if not exists public.msgu_messages (
  id uuid primary key default gen_random_uuid(),
  chat_id uuid not null references public.msgu_chats(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 3000),
  created_at timestamptz not null default now()
);
create index if not exists msgu_message_timeline on public.msgu_messages(chat_id,created_at desc);
create index if not exists msgu_chat_a on public.msgu_chats(user_a,created_at desc);
create index if not exists msgu_chat_b on public.msgu_chats(user_b,created_at desc);

create or replace function public.msgu_create_profile()
returns trigger language plpgsql security definer set search_path = '' as $$
declare name text;
begin
  name := nullif(trim(left(coalesce(new.raw_user_meta_data->>'display_name',''), 50)), '');
  if name is null or char_length(name) < 2 then name := 'MsgU User'; end if;
  insert into public.msgu_profiles(id,username,display_name)
    values (new.id, 'user_' || substr(replace(new.id::text,'-',''),1,12), name);
  return new;
end
$$;
-- Signup trigger requires elevated privileges; block direct RPC calls by app users.
revoke all on function public.msgu_create_profile() from public, anon, authenticated;
drop trigger if exists msgu_profile_signup on auth.users;
create trigger msgu_profile_signup after insert on auth.users
  for each row execute function public.msgu_create_profile();

-- Backfill existing Supabase Auth accounts if you have already created test users.
insert into public.msgu_profiles(id,username,display_name)
select u.id, 'user_' || substr(replace(u.id::text,'-',''),1,12), 'MsgU User'
from auth.users u on conflict (id) do nothing;

alter table public.msgu_profiles enable row level security;
alter table public.msgu_chats enable row level security;
alter table public.msgu_messages enable row level security;

drop policy if exists msgu_profile_read on public.msgu_profiles;
create policy msgu_profile_read on public.msgu_profiles for select to authenticated using (
  id = (select auth.uid()) or discoverable or exists (
    select 1 from public.msgu_chats c
    where (c.user_a = (select auth.uid()) and c.user_b = id)
       or (c.user_b = (select auth.uid()) and c.user_a = id)
  )
);
drop policy if exists msgu_profile_update on public.msgu_profiles;
create policy msgu_profile_update on public.msgu_profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));
drop policy if exists msgu_chat_read on public.msgu_chats;
create policy msgu_chat_read on public.msgu_chats for select to authenticated
  using ((select auth.uid()) in (user_a,user_b));
drop policy if exists msgu_chat_decide on public.msgu_chats;
create policy msgu_chat_decide on public.msgu_chats for update to authenticated
  using ((select auth.uid()) in (user_a,user_b)
    and requested_by <> (select auth.uid()) and status='requested')
  with check ((select auth.uid()) in (user_a,user_b)
    and requested_by <> (select auth.uid()) and status in ('accepted','rejected'));
drop policy if exists msgu_message_read on public.msgu_messages;
create policy msgu_message_read on public.msgu_messages for select to authenticated
  using (exists (select 1 from public.msgu_chats c
    where c.id=chat_id and c.status='accepted' and (select auth.uid()) in (c.user_a,c.user_b)));
drop policy if exists msgu_message_create on public.msgu_messages;
create policy msgu_message_create on public.msgu_messages for insert to authenticated
  with check (sender_id = (select auth.uid()) and exists (
    select 1 from public.msgu_chats c where c.id=chat_id and c.status='accepted'
      and (select auth.uid()) in (c.user_a,c.user_b)));

revoke all on public.msgu_profiles, public.msgu_chats, public.msgu_messages from anon;
revoke all on public.msgu_profiles, public.msgu_chats, public.msgu_messages from authenticated;
grant select on public.msgu_profiles, public.msgu_chats, public.msgu_messages to authenticated;
grant update(username,display_name,discoverable) on public.msgu_profiles to authenticated;
grant update(status) on public.msgu_chats to authenticated;
-- The client supplies its own unique message id for retry-safe idempotent sends.
grant insert(id,chat_id,sender_id,body) on public.msgu_messages to authenticated;

-- Limited SECURITY DEFINER: chat request is only permitted to an explicitly discoverable username.
create or replace function public.msgu_start_chat(handle text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare me uuid; peer uuid; a uuid; b uuid; chat_id uuid;
begin
  me := auth.uid();
  if me is null then raise exception 'Sign in required'; end if;
  if handle is null or handle !~ '^[a-z0-9_]{3,20}$' then
    raise exception 'Invalid username';
  end if;
  select id into peer from public.msgu_profiles
    where username=handle and discoverable=true;
  if peer is null then raise exception 'User not found or not discoverable'; end if;
  if peer=me then raise exception 'Cannot message yourself'; end if;
  a := least(me,peer); b := greatest(me,peer);
  insert into public.msgu_chats(user_a,user_b,requested_by)
    values (a,b,me) on conflict(user_a,user_b) do nothing;
  select id into chat_id from public.msgu_chats where user_a=a and user_b=b;
  return chat_id;
end
$$;
revoke all on function public.msgu_start_chat(text) from public, anon;
grant execute on function public.msgu_start_chat(text) to authenticated;
