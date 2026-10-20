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

## Historical comparison requested by user

Compared the checked-in 1.1.2 baseline (`1a12183`), 1.3.3 release (`784c0a8`) and 1.3.4 hotfix (`a7fd04b`). The user has not yet identified their exact last working version.

### Static comparison
- All three use the same keyless mirror: `https://mod.mcimirror.top/curseforge/v1`.
- Default Mods browsing uses the same search parameters: gameId 432, classId 6, empty searchFilter, sortField 1, sortOrder desc, pageSize **30**, index 0. The earlier Python smoke check mistakenly used pageSize 20; it is now corrected. Its minimal request also failed, so the earlier 503 was not evidence of a particular page-size rejection.
- 1.1.2 supports one category, newer versions multiple categories. With Categories: All (the supplied screenshot), neither adds a category filter.
- Official-key-first/mirror-fallback behavior was already present before 1.3.4. No alternate formerly working mirror URL was found in these snapshots.
- 1.3.4 added an explicit Aerix User-Agent, JSON Accept header, finite timeouts, response validation and shared installer routing. Authenticated requests no longer automatically follow redirects; keyless mirror requests still do.

### Live old-versus-new client comparison

[Run 39408551801](https://github.com/entitybrian69-bit/Aerix-launcher/actions/runs/39408551801), comparison source `b19b2b2`:

```json
{"baseline_1_1_2":{"http_status":503,"valid_list":false},"current_1_3_4":{"valid_list":false},"release_1_3_3":{"http_status":503,"valid_list":false}}
```

The script compiles each snapshot's actual ApiHandler (and the current CurseForgeApiHandler) with the repository's Gson jar, using the same HashMap search parameters and no key. Android logging/container/read helpers are stubbed; HTTP and JSON code are real. Historical clients receive JVM safety timeouts to prevent their formerly unbounded requests hanging CI. Raw response bodies and historical logs are not published. These are JVM runner checks, **not Android tests**; the old default JVM User-Agent is not a reconstruction of the phone's Dalvik User-Agent.

A contemporaneous independent HTTP check with the corrected 30-result query returned 503 for both exact and minimal requests. Official access remained unconfigured. The workflow correctly concluded failure rather than treating compilation or fixture tests as working live search.

### Conclusion and limits

The older Java clients also fail against the mirror now, so the check does not support blaming only the new response parser or explicit Aerix headers. This is consistent with previously working mirror-backed access becoming unavailable. It does not prove when it stopped working, whether it is unavailable from every network, or what exact HTTP status the phone received. No rollback, borrowed credential, substituted content provider, or new APK release is justified by these results. A legitimate official API key is an alternative route, not a prerequisite that historically existed for keyless mirror browsing.

Local Python regression suite: 7 tests passed. No launcher production code changed in this comparison.
