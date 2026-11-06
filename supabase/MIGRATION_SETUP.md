# Notes migration history

The `notes_server_revision` migration exists in this repository. The Ordernew
project (`pafgvntgilogmxfrjamp`) already received its SQL through the SQL Editor;
its migration history was empty when checked. Do not run the migration again
against that project: `create table public.notes` would fail because it exists.

Before using `supabase db push` against Ordernew:

1. Confirm that the linked project is `pafgvntgilogmxfrjamp` and that `public.notes`,
   the `notes_set_revision` trigger, grants and `notes_all_active_staff` RLS
   policy still match the migration. Inspect the project and migration history
   again; do not assume the earlier inspection is still current.
2. With an authorized Supabase CLI session linked to Ordernew, record the
   existing migration as applied without running its SQL:

   ```sh
   supabase migration repair 20261106011919 --status applied --linked
   ```

3. Run `supabase migration list --linked` and verify the local and remote entries
   match. This records migration history; it does not validate schema contents.

Do not mark this migration applied in a fresh project without first creating and
verifying the schema. Use a separate non-production project for end-to-end tests.
