-- Undo 20261121110000_purchases_follow_user.sql: removes the link only. Purchase rows deleted by the migration are not restored.
alter table public.web_purchases drop constraint if exists web_purchases_user_id_fkey;
