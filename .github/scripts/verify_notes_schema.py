"""Read-only checks before recording an already-installed Notes migration."""
import json
import os
import sys
import urllib.error
import urllib.request

PROJECT = "pafgvntgilogmxfrjamp"
QUERY = """
select
 (select count(*) from information_schema.columns where table_schema='public' and table_name='notes') as columns_count,
 (select count(*) from information_schema.columns where table_schema='public' and table_name='notes' and
  ((column_name='id' and data_type='text' and is_nullable='NO') or
   (column_name in ('title','content') and data_type='text' and is_nullable='NO') or
   (column_name='is_pinned' and data_type='boolean' and is_nullable='NO') or
   (column_name='created_by' and data_type='uuid' and is_nullable='YES') or
   (column_name='updated_at' and data_type='timestamp with time zone' and is_nullable='NO') or
   (column_name='revision' and data_type='bigint' and is_nullable='NO'))) as columns_valid,
 (select count(*) from information_schema.columns where table_schema='public' and table_name='notes' and
  ((column_name='is_pinned' and column_default='false') or (column_name='updated_at' and column_default='now()')
   or (column_name='revision' and column_default='1'))) as defaults_valid,
 (select relrowsecurity from pg_class where oid=to_regclass('public.notes')) as rls,
 (select count(*) from pg_policies where schemaname='public' and tablename='notes' and policyname='notes_all_active_staff'
  and cmd='ALL' and roles::text='{authenticated}'
  and qual='(current_app_role() = ANY (ARRAY[''admin''::text, ''staff''::text]))' and with_check=qual) as policy_valid,
 (select count(*) from pg_policies where schemaname='public' and tablename='notes') as policy_count,
 (select count(*) from pg_trigger where tgrelid=to_regclass('public.notes') and tgname='notes_set_revision'
  and tgenabled='O' and not tgisinternal and tgtype=23 and tgfoid=to_regprocedure('public.set_notes_revision()')) as trigger_valid,
 (select count(*) from pg_trigger where tgrelid=to_regclass('public.notes') and not tgisinternal) as trigger_count,
 (select regexp_replace(lower(prosrc),'[[:space:]]','','g') from pg_proc where oid=to_regprocedure('public.set_notes_revision()')) as function_body,
 (select count(*) from pg_constraint where conrelid=to_regclass('public.notes') and
  ((contype='p' and pg_get_constraintdef(oid)='PRIMARY KEY (id)') or
   (contype='f' and pg_get_constraintdef(oid)='FOREIGN KEY (created_by) REFERENCES auth.users(id)') or
   (contype='c' and pg_get_constraintdef(oid)='CHECK ((revision > 0))'))) as constraints_valid,
 (select count(*) from pg_constraint where conrelid=to_regclass('public.notes')) as constraints_count,
 has_table_privilege('authenticated',to_regclass('public.notes'),'SELECT') as auth_read,
 has_table_privilege('authenticated',to_regclass('public.notes'),'INSERT') as auth_insert,
 has_table_privilege('authenticated',to_regclass('public.notes'),'UPDATE') as auth_update,
 has_table_privilege('authenticated',to_regclass('public.notes'),'DELETE') as auth_delete,
 has_table_privilege('anon',to_regclass('public.notes'),'SELECT') as anon_read,
 has_table_privilege('anon',to_regclass('public.notes'),'INSERT') as anon_insert,
 has_table_privilege('anon',to_regclass('public.notes'),'UPDATE') as anon_update,
 has_table_privilege('anon',to_regclass('public.notes'),'DELETE') as anon_delete
"""
EXPECTED = {
    'columns_count': 7, 'columns_valid': 7, 'defaults_valid': 3, 'rls': True,
    'policy_valid': 1, 'policy_count': 1, 'trigger_valid': 1, 'trigger_count': 1,
    'constraints_valid': 3, 'constraints_count': 3,
    'function_body': "beginiftg_op='insert'thennew.revision:=1;elsenew.revision:=old.revision+1;endif;returnnew;end;",
    'auth_read': True, 'auth_insert': True, 'auth_update': True, 'auth_delete': True,
    'anon_read': False, 'anon_insert': False, 'anon_update': False, 'anon_delete': False,
}


def main():
    token = os.environ.get('SUPABASE_ACCESS_TOKEN')
    if not token:
        sys.exit('SUPABASE_ACCESS_TOKEN is missing')
    request = urllib.request.Request(
        f'https://api.supabase.com/v1/projects/{PROJECT}/database/query',
        data=json.dumps({'query': QUERY}).encode(),
        headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'},
        method='POST',
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            rows = json.load(response)
    except (urllib.error.URLError, ValueError, TimeoutError):
        sys.exit('Schema validation request failed; check project access and connectivity.')
    if not isinstance(rows, list) or len(rows) != 1 or not isinstance(rows[0], dict):
        sys.exit('Schema validation returned an unexpected result; repair was not run.')
    mismatches = [name for name, value in EXPECTED.items() if rows[0].get(name) != value]
    if mismatches:
        sys.exit('Schema validation failed for: ' + ', '.join(mismatches) + '. Repair was not run.')
    print('Notes schema, RLS, grants, constraints and revision trigger verified.')


if __name__ == '__main__':
    main()
