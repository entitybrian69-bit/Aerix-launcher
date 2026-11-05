# Aerix game authentication

Aerix-authenticated game accounts use your Aerix Social account. On the home
dashboard tap **Sign in to Aerix account**, continue with Google (or choose email),
and choose a permanent game name on the account page. Then tap **Sign in with Aerix** (or choose Aerix in Accounts). The
same Social account and game name can be used on another device. Existing
Microsoft, Ely.by and offline accounts are not removed. Names are unique within
Aerix, ignoring case, and the name and game UUID are public.

The authenticated UUID is the normal offline UUID for that name, to preserve
world data when switching an existing offline name to Aerix authentication. That
does **not** make offline servers secure: anyone may still impersonate a name on
an offline-mode server. Aerix accounts authenticate only on servers deliberately
configured to trust Aerix. Microsoft-only servers still require a legitimate
Microsoft Minecraft account.

## Appearance and privacy

Publish or restore your skin and imported cape on Skin & Cape. Published images
are publicly accessible by URL, which includes your Social user ID. Choose
**Share game appearance** to expose them through your public game profile; turn
sharing off to stop new profile lookups, although previously downloaded or cached
images cannot be recalled. The published skin includes the Wide/Slim setting.
Restoring a skin or imported cape backs up the replaced file under the skins
folder's `cloud-backups`. Save/restore the animated Client cape choice separately;
remote cape animation is not guaranteed. Availability varies by Minecraft
version and server/client setup.

## For server operators

Back up your server and player data before changing authentication. Configure a
Java server to run in **online mode**, with a compatible authlib-injector attached
to the server JVM. This example assumes the injector and server JARs are in the
working directory:

```text
java -javaagent:authlib-injector.jar=https://lktupxfszfefsoyksfkg.supabase.co/functions/v1/aerix-auth -jar server.jar nogui
```

Set `online-mode=true` in `server.properties`. For versions supporting secure
profile enforcement, set `enforce-secure-profile=false`: Aerix does not issue
Microsoft-signed chat/profile keys. Disabling it reduces the assurances of
secure chat. Restart the server after changing either setting. Get the injector
from its official release: https://github.com/yushijinhun/authlib-injector/releases/tag/v1.2.8

Use this only on a server where you trust Aerix as the authentication provider;
it is **not** the same as a Microsoft-only server. Do not switch online mode off
or expose an unprotected offline backend to the internet. Proxy networks have
additional forwarding/security requirements and are not covered by this setup.
Aerix offline UUIDs differ from Microsoft UUIDs; players migrating from Microsoft
authentication can see different inventories/world data unless an operator
carefully migrates it. Test with backups before allowing players in.

## Technical notes

The deployed schema uses separate owner-scoped game sessions and bounded,
single-use join records; direct session and key tables are not available to
anonymous or authenticated API roles. An Edge Function exposes the
Yggdrasil-compatible endpoint and signed texture properties. Textures are
bounded and sanitized; unsupported PNGs can be omitted. The 1.20.1 authlib and
the bundled agent have been exercised against the live endpoint. This is a
debug implementation and has not been tested on a physical Android device or
against a running multiplayer server across all Minecraft versions.
