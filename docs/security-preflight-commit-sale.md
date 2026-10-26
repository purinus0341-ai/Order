# Ordernew Security Preflight — commit_sale

## Scope

This branch changes the bootstrap SQL only. It does not deploy any database change to Supabase Production.

## Change made

Both definitions of `public.commit_sale` in `supabase_setup.sql` no longer create a missing profile as `staff` and no longer reactivate an inactive profile. A caller with no active profile is rejected with `not_authorized`.

## Required verification before production rollout

Run against a staging database first, using dedicated test accounts and a disposable test order:

1. Active staff profile: a valid sale succeeds.
2. Active admin profile: a valid sale succeeds.
3. Missing profile: `commit_sale` fails with `not_authorized`; no row is inserted into `public.profiles`.
4. Inactive profile: `commit_sale` fails with `not_authorized`; `is_active` remains false.
5. Role outside `admin` / `staff`: sale fails with `not_authorized`.
6. Repeat the same sale with the same idempotency key: no duplicate order or stock/points side effects.
7. Confirm the app's onboarding/admin workflow explicitly provisions new staff before they can sell.

## SQL review checks

After deploying the migration in staging, confirm both the live function and the grants:

```sql
select p.proname,
       p.prosecdef as security_definer,
       has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute,
       pg_get_functiondef(p.oid) as definition
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname = 'commit_sale';
```

The live function should contain no profile auto-provision/reactivation branch. Review and tighten grants separately after checking the app's intended RPC call path; do not revoke execute privileges in bulk without a staging compatibility test.

## Rollback

Do not roll back by restoring the unsafe auto-provisioning behavior. If the change blocks legitimate onboarding, fix the explicit admin provisioning flow, then retest. Production rollout requires a backup, a reviewed migration, and a maintenance/rollback plan.
