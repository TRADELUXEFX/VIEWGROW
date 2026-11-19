-- ViewGrow: History (People and an opened day) no longer shows people who are not members any more.
-- Run once in the Supabase SQL Editor. Safe to run twice. Replaces any earlier version of this file.
-- Covers both ways of deleting: the member's own Delete my account (app) and the admin's Delete user button
-- (which does not use the deleted list). A received person is shown only while a member with that number exists.
-- Changes ONLY web_people and web_day_contacts (one extra condition each). Nothing is deleted.
-- web_people's total counts only the people it lists, so "load more" stays correct.
-- NOT changed: the contact limit counter (the 3/500), web_received, web_history and the day cards' +count.
-- Banned members are NOT hidden: each person in the answer also carries "banned": true/false, and the app shows a red Banned tag.
-- Grants and owner stay as they are.
-- The guard stops with an error, changing nothing, if something is missing.

do $$
begin
  if to_regclass('public.web_users') is null then
    raise exception 'web_users is missing. Nothing was changed.';
  end if;
  if position('web_received' in pg_get_functiondef('public.web_people(text,text,integer,integer)'::regprocedure)) = 0
     or position('web_received' in pg_get_functiondef('public.web_day_contacts(text,text,date)'::regprocedure)) = 0 then
    raise exception 'web_people or web_day_contacts is not the expected version. Nothing was changed.';
  end if;
end $$;

create or replace function public.web_day_contacts(p_phone text, p_pin text, p_day date)
 returns jsonb
 language plpgsql
 security definer
 set search_path to 'public', 'pg_temp'
as $function$
declare a record; v_items jsonb;
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;
  if p_day is null then
    return jsonb_build_object('ok', false, 'code', 'BAD_DAY');
  end if;
  select coalesce(jsonb_agg(jsonb_build_object('name', x.name, 'phone', x.phone,
                                               'banned', coalesce((select m.banned from public.web_users m where m.phone = x.phone limit 1), false))
                            order by x.received_at, x.phone), '[]'::jsonb)
    into v_items
    from public.web_received x
   where x.user_id = a.uid and x.day = p_day
     and exists (select 1 from public.web_users m where m.phone = x.phone);
  return jsonb_build_object('ok', true, 'day', p_day, 'items', v_items);
end
$function$;

create or replace function public.web_people(p_phone text, p_pin text, p_offset integer default 0, p_limit integer default 50)
 returns jsonb
 language plpgsql
 security definer
 set search_path to 'public', 'pg_temp'
as $function$
declare a record; v_items jsonb; v_total int;
begin
  select * into a from public._web_auth(p_phone, p_pin);
  if a.err is not null then return a.err; end if;
  select count(*)::int into v_total from public.web_received r
   where r.user_id = a.uid
     and exists (select 1 from public.web_users m where m.phone = r.phone);
  select coalesce(jsonb_agg(jsonb_build_object('name', t.name, 'phone', t.phone, 'day', t.day, 'banned', t.banned)
                            order by t.received_at desc, t.phone), '[]'::jsonb)
    into v_items
    from (
      select r.name, r.phone, r.day, r.received_at,
             coalesce((select m.banned from public.web_users m where m.phone = r.phone limit 1), false) as banned
        from public.web_received r
       where r.user_id = a.uid
         and exists (select 1 from public.web_users m where m.phone = r.phone)
       order by r.received_at desc, r.phone
       limit greatest(least(coalesce(p_limit, 50), 100), 1)
      offset greatest(coalesce(p_offset, 0), 0)
    ) t;
  return jsonb_build_object('ok', true, 'total', v_total, 'items', v_items);
end
$function$;
