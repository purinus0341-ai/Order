# Login profile fail-closed change: validation plan

## Scope
This change is limited to Android login handling in `SupabaseClient.login()`. It removes the local fallback that fabricated an active `staff` profile when the Supabase profile lookup failed.

## Expected behavior
- Valid Auth user with an existing active `public.profiles` row: login succeeds and the server profile is cached.
- Auth succeeds but the profile row is missing: login fails, access/refresh tokens and cached profile are cleared.
- Profile lookup returns an authorization/server error: login fails and session state is cleared.
- Profile lookup fails due to network/timeout: login fails and session state is cleared; user can retry after connectivity is restored.
- Auth response has no user ID: login fails and session state is cleared.
- Profile exists but `is_active = false`: login fails and session state is cleared.

## Manual test procedure
Run only against a staging Supabase project or designated test accounts. Do not alter production profiles for testing.

1. Active test account with a valid profile: verify login succeeds and the displayed role matches the server profile.
2. Auth account without a profile row: verify login is rejected and no local `staff` profile is created.
3. Inactive test account: verify login is rejected.
4. Simulate profile endpoint failure (for example, block network access after Auth succeeds): verify login fails and the app does not retain an authenticated session.
5. Retry with network restored: verify the active test account can log in normally.
6. Inspect local preferences or app behavior after each failure to confirm the access token, refresh token, expiry and user profile were cleared.

## Build verification
- Run the repository's existing Android CI workflow on this branch, if configured.
- Otherwise, run `./gradlew :app:assembleDebug` in a configured Android/Java 17 environment with the required `SUPABASE_ANON_KEY` environment variable.
- Confirm the build succeeds before generating or distributing an APK.

## Safety boundaries
- This document does not authorize applying the Supabase migration to production.
- Do not merge or deploy until build and staging tests pass.
- Keep the existing signing configuration unchanged until the installed APK's signing certificate has been compared with the candidate APK.
