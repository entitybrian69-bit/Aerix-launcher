# CurseForge search follow-up — 2026-10-20

User's 1.3.4 screenshot still shows Discover → Mods → CurseForge → “Unable to reach the platform”, with all filters at their defaults. The screenshot establishes a search failure, not a file-download failure.

## Live evidence

[Metadata probe run 39406846815](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39406846815), source `35addb8dcc89e3f078fd14ca978f09a203973095`:

```json
{"build_key_present": false, "mirror_exact_search": {"result": "http_error", "status": 503}, "mirror_minimal_search": {"result": "http_error", "status": 503}, "official": {"result": "not_configured"}}
```

The official request was skipped because no usable `CURSEFORGE_API_KEY` Actions secret was available to that job. There is no tracked `curseforge_key.txt`; Android CI does not generate one. Without a user-configured key, the release therefore relies on the public metadata mirror. That mirror returned HTTP 503 from this runner, with both the current Discover query and a minimal search. This is a concrete live access blocker consistent with the phone screenshot; it is not proof of the phone's exact HTTP status or a universal mirror outage.

The prior deterministic parser/routing tests do not verify service availability. Do not publish another release claiming search works merely because those tests pass. The probe now exits unsuccessfully when neither the official route nor the exact mirror search returns a valid list. A minimal request passing alone does not validate Discover.

## Required next step

Configure a legitimate Aerix CurseForge API key through GitHub repository Settings → Secrets and variables → Actions → New repository secret, named `CURSEFORGE_API_KEY`. Do not paste it into chat, commit it, borrow another launcher's key, or send it to the mirror. Re-run **CurseForge live metadata check** on the current session branch and verify official search returns a valid list before rebuilding/publishing. For a private immediate check, the existing launcher Settings → CurseForge API key field can also use the owner's key; never share a screenshot containing it.

A successful runner request still needs phone confirmation. If official access works in CI but the phone still fails, capture a redacted launcher log to distinguish Android connectivity/TLS from parsing. Do not prescribe reinstalling or clearing data.

No additional APK release was published for this investigation. Social design review is paused again while CurseForge access is unresolved.
