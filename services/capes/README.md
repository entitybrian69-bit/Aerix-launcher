# Self-hosted cape service

This service is separate from Ely.by and Microsoft. It never accepts Minecraft passwords or account access tokens. Capes are public by UUID. Other players need the companion mod, configured with the same HTTPS endpoint, to see them. Vanilla clients do not receive these capes.

## Deployment

Use Python 3.11+, install `requirements.txt`, set `AERIX_CAPE_DATA` to persistent storage and `AERIX_CAPE_KEYS` to a JSON object mapping canonical UUIDs to SHA-256 upload-key digests. An empty mapping disables all uploads.

After verifying an account's UUID ownership yourself, run `python provision.py UUID`. Give the generated upload key privately to that owner and merge the printed digest entry into the environment mapping. Never use a Minecraft token as the upload key. Automatic identity verification is not implemented.

Run `uvicorn server:app --host 0.0.0.0 --port 8000` behind an HTTPS reverse proxy. Configure a 1 MB request-body limit, request timeouts, an upload rate limit, a connection limit, and a disk quota at the proxy/container. Do not expose the development server directly to the internet. Restart after changing the key mapping. Back up the persistent data directory.

Configure the HTTPS endpoint in Aerix Studio → Shared capes, then install the companion into a Fabric 1.20.1 instance. On desktop clients, place the companion JAR in `mods` and write `config/aerix-capes.json` containing `{"endpoint":"https://your-host"}`. Enable the Cape skin part in Minecraft. PNG capes must be static 64 × 32 images. Updates may take one minute to appear.

## Checks

Install `pytest httpx` in a development environment; run `python -m pytest tests` from this directory.
