# Notes migration history

The `notes_server_revision` migration exists in this repository. The Ordernew
project (`pafgvntgilogmxfrjamp`) received its SQL through the SQL Editor. On
2026-11-06, workflow 42168563911 verified the existing schema and recorded
version `20261106011919` as applied. A separate migration-list check confirmed
the record. Do not run the SQL again against that project: `public.notes` exists.

If a future inspection shows the record is missing, before using
`supabase db push` against Ordernew:

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
