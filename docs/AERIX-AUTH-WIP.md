# Aerix game authentication — work in progress

This is not ready for players or server operators. The application changes on this
branch have compiled, but no end-to-end game/server authentication test has passed.
Do not distribute an APK containing this code as a working Aerix authentication release.

The `aerix_game_authentication_sessions` migration has been applied to the Aerix
Supabase project. A rollback-only test exercised session issuance, invalid joins,
single-use verification and revocation. The public `aerix-auth` Edge Function is
intentionally unavailable (HTTP 503) while deployment is being repaired. Its
implementation is in `supabase/functions/aerix-auth/index.ts`. An attempted
immutable GitHub module import failed to start on Supabase (`BOOT_ERROR`); deploy a
self-contained function bundle using the supported Supabase deployment tooling,
then check the public metadata and negative requests using
`tests/aerix_auth_live_test.py` and complete real client/server verification.

Remaining work includes a security and compatibility review, rate/abuse testing,
server operator instructions, and an actual multiplayer handshake and appearance
test. Microsoft-only servers still require a legitimate Microsoft account.

Commits tagged `[auth-check]` deliberately skip publishing `debug-latest`; the
existing debug APK predates real authentication and must not be presented as such.
