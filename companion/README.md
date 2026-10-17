# Aerix companion — Fabric 1.20.1 only

Build with Java 17+ and Gradle 8.14.3: `gradle -p companion build`.
The remapped JAR in `companion/build/libs` works on desktop Fabric clients too.
Android CI packages it into the launcher; Studio → Shared capes & companion installs
it into the selected Fabric 1.20.1 instance while Minecraft is stopped.

## Shared capes
Set `config/aerix-capes.json` to `{"endpoint":"https://your-service.example"}`.
Deploy `services/capes` yourself behind HTTPS. The launcher **does not supply a hosted
service**. All viewers need this mod and the same endpoint. Custom capes do not reach
vanilla players; Ely.by cape self-equipping is not provided. Enable Cape in skin settings.
Cache refresh is once a minute, capped at 512 players and 16 outstanding downloads.

## In-game account switching
Launch from Aerix, save/disconnect to the title screen, and choose **Switch account**
in Android's game drawer. Minecraft stays open. This protocol is deliberately unavailable
while a world/server connection exists. The selected account must be saved, unexpired,
and use the same authentication provider that launched the JVM. It changes the running
session, not the launcher's default account. Sign-in/refresh and provider changes need the
launcher; provider changes and Realms need a restart. Realms' cached client is not rebuilt.

The private Android files directory carries a short-lived request and a heartbeat.
No credentials are placed in game directories, backups, logs, or public endpoints.
Requests are deleted before processing and stale requests are discarded at client startup.
Authentication and cape downloads run off the render thread; session mutation occurs
only on the client thread after rechecking that the client is disconnected.

This is version-specific mixin code, not a general hot-login API. A successful build is
not proof of live-server compatibility. Verify on a device using a disposable world,
offline/Microsoft/Ely accounts, chat signing, reconnect, and authentication-provider
boundaries before relying on it for important sessions. Keep backups.
