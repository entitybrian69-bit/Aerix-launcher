# Aerix authentication testing status

The `aerix_game_authentication_sessions` migration and the self-contained
`aerix-auth` Edge Function are deployed. Automated checks exercise live public
metadata, invalid sessions, and a synthetic authenticated handshake using actual
Minecraft 1.20.1 authlib with the bundled authlib-injector. The synthetic account
is temporary and must be removed after fixture checks. Direct access to session,
join and signing-key tables is denied to anonymous and authenticated API roles.

Still needed: testing on a real Android device and a running multiplayer server,
additional Minecraft-version compatibility tests, load/rate-limit evaluation, and
independent security review. See `AERIX-GAME-ACCOUNTS.md` for usage and server
operator setup.
