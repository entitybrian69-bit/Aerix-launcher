# Trinkies cosmetics integration

Trinkies is the client-side cosmetics bridge bundled with Aerix Launcher. The Fabric mod metadata uses the `trinkies` ID and is built from `aerix_client_mod`; the APK carries the mod JAR as an asset.

## Install scope

- Existing Fabric, Quilt, and Legacy Fabric instances are updated locally when the Home screen opens.
- Newly created/imported supported instances are repaired again immediately before launch.
- Aerix Client instances keep their existing install/update path.
- Vanilla, Forge, and NeoForge instances are not modified and no loader is silently installed for them.

## Available in this build

- The launcher character is a touch-rotatable 3D preview with idle, wave, walk, run, fly, and crouch poses.
- Existing account skin import, saved skins, wide/slim model selection, and standard cape import are reused.
- Fifteen existing animated cape designs sync to supported instance folders. An imported cape can also be selected for Trinkies.
- Four pixel-art headwear overlays are applied to the skin's outer head layer and to the local preview/compatible skin-service response.

## Visibility limits

The bundled mod is client-only. It can render the equipped appearance in that player's own Trinkies-compatible game client; it does not implement a server plugin or client-to-client cosmetic packet protocol. Other players are not guaranteed to see custom skins or capes, and pets, attached back items, and synchronized multiplayer emotes are collection placeholders rather than shipped in-game features. A vanilla-compatible skin/cape path depends on the authentication/skin service and server configuration; unmodified vanilla clients and servers cannot display Trinkies-only attachments or animations.
