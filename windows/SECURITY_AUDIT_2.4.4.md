# Aerix Launcher for Windows 2.4.4 — Security Audit

**Scope:** `windows/aerix-desktop` (47 Kotlin files, ~16.3k lines), the packaging path
(`build.sh`, `windows-desktop.yml`) and every runtime artefact shipped for Windows.
**Target:** Aerix Launcher 2.4.4, the portable ZIP and the jpackage `.exe`.
**Question:** is the VirusTotal detection (Trapmine `Malicious.moderate.ml.score`) a true positive?

---

## 0. Read this first: which binary was scanned

Three things about this investigation need to be stated plainly before any conclusion.

**a) The hash in the request does not match any binary this repository produced.**
The published installer, as recorded by the CI run that built it, is **68,921,856 bytes** with the
SHA-256 in its own `SHA256SUMS.txt`. The hash quoted in the request is
`0bab8cfa…c8792f9`. The artefacts are served from a host this build environment cannot reach, so
the uploaded bytes could not be re-hashed here. **Treat the scanned file as unverified** until its
hash is compared against the release's `SHA256SUMS.txt`:

```
certutil -hashfile AerixLauncher-2.4.4.exe SHA256
```

If it does not match, the scan is of something other than our build and no conclusion in this
document applies to it. Everything below is an audit of the source and of the build we control.

**b) A detection cannot be "fixed" by editing source, and this report does not claim it is.**
Trapmine scores a compiled binary. Nothing in this document asserts the detection is gone —
that can only be established by rescanning the rebuilt artefact, which requires network access
to VirusTotal that this environment does not have.

**c) What changed, and what is only recommended.**
Section 4 lists the fixes that were applied and verified. Section 5 lists the causes that remain,
each with the concrete evidence for why it is a false positive or what it would take to clear it.

---

## 1. Attack surface

### 1.1 Dependencies: none

```
$ grep -rhoE '^import [a-z]' src/main/kotlin | sort -u | grep -vE '^(java|javax|kotlin|net\.kdt)'
(no output)
```

There are **no third-party libraries**. No OkHttp, no Apache Commons, no Gson, no Log4j, no native
code of our own. The only bundled code beyond the application is the Kotlin standard library, which
is a compile-time dependency of the language itself and is shipped from the compiler distribution
included in the build, not fetched from a package index. The installer's runtime is a `jlink` image
of the JDK, not a separately downloaded JRE.

This removes the usual supply-chain questions entirely: there is no dependency tree to compromise,
and no transitive dependency can change without changing the source in this repository.

### 1.2 Process execution

Eight `ProcessBuilder` call sites; all audited.

| Site | Command | Argument source | Shell? |
|---|---|---|---|
| `MicrosoftAuth.openBrowser` | `rundll32 url.dll,FileProtocolHandler <url>` | a URL built from constants | no |
| `GameLauncher.launch` | `<java> @<argfile>` | paths under the game directory | no |
| `GameLauncher` (stop) | `taskkill /PID <pid>` | a numeric pid from `ProcessHandle` | no |
| `JavaRuntimes.probe` | `<java> -XshowSettings:properties -version` | discovered paths | no |
| `Loaders` (×2) | `<java> -jar <installer> --installClient <dir>` | loader jars in the game directory | no |
| `LibraryScreen` | `explorer.exe <path>` | a path the player clicked | no |
| `SelfTest` | `<java> @<argfile>` | test fixtures | no |

No site passes a command string to a shell. The one that used to — `InstanceLinks`, via
`cmd /c mklink` — is the subject of section 2.1.

### 1.3 Network

Every outbound host in the source, grouped by purpose:

| Host | Purpose |
|---|---|
| `piston-meta.mojang.com`, `launchermeta.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net` | Minecraft metadata, libraries, assets |
| `login.live.com`, `*.auth.xboxlive.com`, `api.minecraftservices.com` | Microsoft / Xbox authentication |
| `meta.fabricmc.net`, `maven.fabricmc.net`, `meta.quiltmc.org`, `maven.minecraftforge.net`, `maven.neoforged.net`, `files.minecraftforge.net` | mod loader metadata and installers |
| `optifine.net`, `downloads.betterthanadventure.net` | OptiFine, Better than Adventure |
| `api.adoptium.net` | downloading Java runtimes the player asks for |
| `api.modrinth.com`, `api.curseforge.com` | content browsing (CurseForge only with the player's own API key) |
| `api.github.com` | update check: one HTTPS GET per day, release metadata only |
| `raw.githubusercontent.com` | server banner artwork from LabyMod's server-media repository |

No analytics, no crash reporting, no telemetry endpoint of any kind
(`grep -iE 'telemetry|analytics|sentry|crashlytics|firebug|mixpanel|segment|amplitude'` → no
matches). TLS is the JVM default (`HttpClient` with no custom `SSLContext`, `TrustManager` or
`HostnameVerifier`), so certificates are verified and the trust store is the system one.

The only identifying data sent is a static `User-Agent`:
`AerixLauncher-Windows/1.0 (+https://github.com/…)`. There is no machine id, hardware fingerprint,
installation id or UUID leak.

### 1.4 Persistence

None. No `Run`/`RunOnce` registry writes, no `schtasks`, no `sc create`, no Start Menu or Startup
folder entries, no service. The launcher is a single executable that runs when the player starts it.
The only things it writes are its own data under `%APPDATA%\Aerix Launcher` and the game directory.

### 1.5 Credentials

* The Microsoft flow is the standard device-code/OAuth chain
  (`login.live.com` → XBL → XSTS → `login_with_xbox` → `/minecraft/profile` + `/entitlements/mcstore`),
  identical to the Android launcher. The `client_id` is the public Piston-meta native client id.
* Refresh tokens are **never written in plain text**. They are sealed with Windows DPAPI
  (`CryptProtectData`), which binds the ciphertext to the signed-in Windows user, and fall back to
  AES-GCM with an owner-only key file when DPAPI is unavailable. Access tokens are never written to
  disk at all.
* At launch the session token goes into the game's argument file, which is created with
  owner-only permissions and deleted on exit (and on failure).
* Logs are scrubbed before they are written: `--accessToken`, `refresh_token`, `identityToken`,
  `Authorization` and `x-api-key` all pass through a redaction table.

---

## 2. Findings

### 2.1 Command injection through an instance folder name — **fixed**

**Severity: high in principle, low in practice** (it requires the player to be the attacker), but it
is a genuine vulnerability and the only one the audit found.

`InstanceLinks` created the directory junctions that share `versions`, `libraries` and `assets`
between instances like this:

```kotlin
ProcessBuilder("cmd", "/c", "mklink", "/J",
    target.toAbsolutePath().toString(), source.toAbsolutePath().toString())
```

`cmd.exe` re-parses its command line, and it honours metacharacters **inside an argument**. The
argument is a path built from a player-chosen instance name. `sanitiseName()` strips the characters
Windows forbids in a filename (`<>:"/\|?*`, control characters) but `&` is perfectly legal in a
filename, so an instance called

```
Minecraft & calc
```

turned that command into two. The same applied to `cmd /c rmdir <target>` when removing a link.

It is worth being precise about the risk: the folder is named by the same person who runs the
launcher, so this is not remote code execution. It is still a real injection — it also runs when a
`.mrpack` is imported, because the pack's `index.name` becomes the instance name, so a **downloaded
modpack could choose the folder name**. That upgrades the practical severity considerably.

**Fix.** `WinLinks` (new) calls Win32 directly:

* `CreateFileW` (`FILE_FLAG_OPEN_REPARSE_POINT`) + `DeviceIoControl(FSCTL_SET_REPARSE_POINT)` with an
  `IO_REPARSE_TAG_MOUNT_POINT` buffer — exactly what `mklink /J` does, and, like it, needs no
  administrator rights.
* `RemoveDirectoryW` / `FSCTL_DELETE_REPARSE_POINT` to unlink. The target directory is never
  touched.

There is no command line left to parse. The existing copy fallback is unchanged, so locked-down
filesystems still work.

`sanitiseName()` additionally rejects `& % ^ ; \` $` now. That is defence in depth rather than the
fix: the name also travels onward as the game's `--gameDir` argument and into launcher scripts,
where `%` is expanded.

**Verification.** A new self-test check asserts that the `InstanceLinks` sources contain no `cmd`,
`mklink` or `rmdir` call. It was verified to fail against the vulnerable code and pass after the
fix. A runtime check was written first and **silently passed against the vulnerable code** — the
vulnerable branch only executes when `AppEnv.isWindows` is true, so on any other OS the test was
vacuous. That is recorded here because it is exactly the failure mode a security test must not have.

### 2.2 Instance names could carry shell metacharacters — **fixed** (defence in depth)

See 2.1. Covered by a second check that pins the sanitiser and asserts ordinary names
(`Skyblock`, `Create 1.21 x64`, `Better Than Adventure!`) are left untouched, so the fix cannot
silently rename anyone's instance.

### 2.3 A `by lazy` that could throw took the RAM slider with it — **fixed**

`AppEnv.totalMemoryMb` initialised a `by lazy` from `java.lang.management`. A `by lazy` caches the
*exception*, so if the module were ever absent (as it was, briefly, in the trimmed runtime image —
section 3.2) every later read would throw. It now degrades to the heap size.

### 2.4 Considered and dismissed

| Candidate | Why it is not a finding |
|---|---|
| `explorer.exe <path>` in `LibraryScreen` | Argument list, no shell; the path is the folder the player clicked. |
| `rundll32 url.dll,FileProtocolHandler <url>` in `MicrosoftAuth` | Fallback for opening the login page when `Desktop.browse` is unavailable. The URL is assembled from constants; the only variable part is the device code from Microsoft. |
| `taskkill /PID <pid>` | Kills the game process the launcher itself started, when the player asks to stop a game. |
| Running Forge/NeoForge/OptiFine installer jars | These loaders only ship installers; this is how every launcher installs them. The jars are downloaded over HTTPS from the loader's own Maven and are the official files. |
| Update check against GitHub | One GET a day for release metadata. It never downloads or executes an update; it shows a link. |
| `raw.githubusercontent.com` for server banners | Image URLs from a public catalogue, loaded as pictures. |
| Executing `java` from `PATH` | `JavaRuntimes` scans well-known locations, `JAVA_HOME`, `PATH` and the player's explicit choice; the player can override it in Settings. This is inherent to a launcher. |

---

## 3. The installer

### 3.1 What was wrong

`jpackage` was invoked **without `--runtime-image`**, so it linked in *every* module of the JDK.
The 68.9 MB installer carried `jdk.compiler`, `jdk.jshell`, `jdk.jcmd`, `jdk.jfr` and the rest of
the development tooling — none of which a launcher loads.

### 3.2 The minimal runtime, and how it was got wrong twice

`build.sh` now builds a runtime image with `jlink`:

```
java.base,java.desktop,java.xml,java.net.http,java.management,jdk.unsupported
```

The first attempt omitted `java.management` (used by the RAM slider) and the second omitted
`java.net.http` — the module that `HttpClient` lives in, and therefore **every download and API
call**. Both would have worked on the developer's JDK and failed only in the installed copy. They
were caught by compiling a probe that touches each package and running it under
`--limit-modules` with the proposed set. That probe is now a self-test check
("everything the launcher needs is in the runtime image"), so a future import from an unbundled
module fails in the test suite instead of on a player's machine.

**Measured result:** the installer went from **68,921,856** bytes to **47,468,704** bytes (−31%)
while keeping the same functionality. CI additionally runs the shipped jar's own self test *under
the shipped runtime*, so a runtime that cannot start the app fails the build.

### 3.3 Reproducibility

The jar was **not** reproducible: every entry carried its file's modification time and the manifest
carried the current time. `JarPackMain` now writes one fixed timestamp
(`1980-01-01 00:00:00`, the earliest a ZIP can represent) for every entry including the manifest,
and prints the SHA-256 it wrote.

**Verified here:** two clean builds produce
`51ebc9d36de1180e994f8e874d93df9ebcb4d72fd07c91f0a7f57b462b8a85a0` both times. CI builds twice
and fails if the hashes differ.

The **installer** is not byte-reproducible, and cannot be made so without a great deal of work:
`jlink` embeds a build-specific runtime image, WiX embeds GUIDs and timestamps, and the Authenticode
signature (when present) contains a signing time. What is reproducible is the jar, which is where
the reviewed code lives; the installer is a deterministic function of it plus the JDK and WiX.
That distinction is stated rather than glossed.

### 3.4 Signing and publisher identity

The installer is **unsigned**, and this is the single most important reason Windows shows
"unrecognised app" and the most likely reason a heuristic engine scores it.

Signing is now supported and wired into CI but **inactive**, because no certificate exists in this
repository:

```bash
export AERIX_SIGN_CERT=<base64 PFX>
export AERIX_SIGN_PASSWORD=<password>
./build.sh --installer          # signs and timestamps with DigiCert
```

Without those variables the build prints `installer is UNSIGNED` and produces exactly the same
artifact as before. No identity is invented, and nothing here claims the warning is gone.

**What it would take to clear SmartScreen:** an OV or EV code-signing certificate (EV gives
immediate SmartScreen reputation; OV builds it over time), signed with a hardware token or a cloud
HSM, plus consistent hashes across releases. That is a purchase and an identity decision for the
project owner, not something a build script can supply.

---

## 4. The Trapmine detection

### 4.1 What is being claimed, and not claimed

Trapmine's `Malicious.moderate.ml.score` is a **machine-learning score, not a signature**. It is
not saying "this file contains known-bad code"; it is saying "this binary resembles things the model
was trained to call malicious, with moderate confidence". That distinction decides how it can be
answered: a false positive of this kind is addressed by changing what the binary *is* (signed,
smaller, fewer capabilities) and by getting it re-scanned, not by removing a code path.

**This report does not claim the detection is resolved.** Re-scanning requires uploading the new
binary to VirusTotal, which this environment cannot do. What follows is the evidence that the
binary does not do the things the model is reacting to, and the concrete changes that remove the
structural false-positive triggers.

### 4.2 The model's most likely inputs, and what the evidence says

| Structural trigger | Present? | Evidence |
|---|---|---|
| **No Authenticode signature** | **Yes — and it is the biggest single factor.** | `gh` shows no signing secrets configured; `build.sh` prints `installer is UNSIGNED`. An unsigned executable that is a few days old, has few downloads and no publisher identity is the canonical "unknown binary" input to every reputation and ML model. |
| **Self-extracting installer that writes an executable and a full runtime** | **Yes — until this audit.** | jpackage's `.exe` is a self-extractor. Ours carried the **entire JDK**, including `jdk.compiler` and `jdk.jshell`. A bundle that drops a compiler and a shell on disk is structurally similar to a dropper. Now trimmed to 8 modules (48 MB runtime, 47.5 MB installer). |
| Bundles a JVM/interpreter | Yes — inherent to a Java launcher | Every Minecraft launcher that bundles a runtime does this. Not removable without requiring a system JRE, which would break "works out of the box". |
| Downloads code at runtime | **Yes, by design** | It downloads Minecraft libraries, loader installers and Java runtimes *after the player installs a version*. This is what a launcher does; the `.exe` itself downloads nothing during installation. |
| Network callback to an unknown host | No | Section 1.3: every host is Mojang, Microsoft, a mod loader's Maven, or GitHub. No analytics, no telemetry. |
| Persistence | No | Section 1.4: no Run key, no scheduled task, no service, no startup entry. |
| Shell execution of user input | **Yes — until this audit.** | Section 2.1. Now removed. |
| Packed/obfuscated code | No | The jar contains plain `.class` files from plain Kotlin source in this repository. There is no obfuscator, no packer and no custom class loader. This can be verified by extracting `aerix-launcher.jar` and running any decompiler. |
| Android-related oddity | Cosmetic, but real | The desktop port was built from an Android launcher, so the jar carries `aerix/` resources and Java streams named after the Android origins (`PojavApplication` is even referenced in a comment). Nothing Android-specific ships, but a scanner reading strings sees "Android" and "PojavLauncher" next to network code. |

### 4.3 Assessment

**Most likely a false positive**, on this evidence:

1. No persistence, no telemetry, no shell execution of input, no third-party dependencies, and
   every network destination is a first-party Minecraft/Microsoft endpoint.
2. The source is public, unobfuscated and small enough to review in an afternoon.
3. The two structural things that *did* look like malware behaviour — an oversized runtime bundle
   and a shell call — were real and are fixed.
4. The score is "moderate", and the engines that do heuristic and behavioural analysis
   (Microsoft, BitDefender, Malwarebytes, and the rest of the list) did not flag it.

**But that is an assessment, not proof**, and two facts keep it from being more:

* The scanned binary's hash does not match the one this repository produced (section 0a), so it is
  possible the scan was of a different build entirely.
* There is no re-scan after the changes in this audit. Until that happens, the honest statement is
  "no malicious behaviour was found in the source or in the build, and the packaging causes most
  likely to produce this score have been addressed", not "the detection is gone".

### 4.4 What would settle it

1. Rebuild (this audit), then verify the hash against the release's `SHA256SUMS.txt`.
2. Rescan the new `.exe` on VirusTotal; if Trapmine still flags it, submit a false-positive report
   to Trapmine directly — they accept them and typically respond within days.
3. Obtain a code-signing certificate and sign every release. This is the only step that changes
   what Windows itself thinks of the file, and therefore what every reputation-based engine thinks.
4. Add the hash and the VirusTotal link to the release notes, so users can check the file they have
   before running it.

---

## 5. What was changed, and the evidence for it

| # | Change | Verified how |
|---|---|---|
| 1 | `cmd /c mklink` and `cmd /c rmdir` replaced with direct Win32 calls (`WinLinks.kt`) | New check asserts the sources contain no `cmd`/`mklink`/`rmdir`; **verified to fail** against the vulnerable code |
| 2 | `sanitiseName` rejects `& % ^ ; \` $` | New check pins it, and asserts ordinary names are untouched |
| 3 | Minimal `jlink` runtime (8 modules instead of the whole JDK) | Installer **68,921,856 → 47,467,008 bytes**; CI runs the shipped jar's self test **under the bundled runtime** and requires 0 failures |
| 4 | Module-list completeness | New check loads every JDK package the launcher uses; caught `java.net.http` and `java.management` before shipping |
| 5 | Reproducible jar | Two clean builds both produce `51ebc9d3…`; CI builds twice and fails on a mismatch |
| 6 | Optional Authenticode signing | `osslsigncode` path in `build.sh`, wired to `AERIX_SIGN_CERT`/`AERIX_SIGN_PASSWORD`; inactive until a certificate exists, and says so |
| 7 | `totalMemoryMb` no longer caches an exception | Covered by the module check (it exercises the same call path) |

**Test state:** 20 checks, 20 passed, 0 failed — run on Linux here and on the Windows runner.
The suite is in `ui/shell/SelfTest.kt`; `./build.sh --selftest` fails the build on any failure.

### 5.1 Current artefacts

Built by the `Windows desktop launcher` workflow on `windows-latest`, after all of the above:

| File | Size | SHA-256 |
|---|---|---|
| `AerixLauncher-2.4.4.exe` | 47,467,008 | `130ab57ac14ad9a820172ebc3be32fa15c0f14b72d1510360347280196d20d55` |
| `AerixLauncher-2.4.4-portable.zip` | 11,597,969 | `8a3e15c30911a6076670ed4869cfb62a52f6534386cc233507d89804a50c5ba9` |

Bundled runtime: `java.base java.datatransfer java.desktop java.management java.net.http
java.prefs java.xml jdk.unsupported` (48 MB, versus the full JDK).

`java.prefs` and `java.datatransfer` are pulled in automatically by `java.desktop`.

### 5.2 Compatibility

* Windows 10 and 11, x64: unchanged. The runtime is jlink'd from Temurin 21, which supports both.
* Minecraft launching: unchanged. The game is launched by whichever Java matches the version's
  `javaVersion` requirement, including the bundled runtime when nothing else is installed —
  `JavaRuntimes.pick` still prefers an exact match and falls back to a newer runtime.
* Microsoft authentication: unchanged. The DPAPI bridge and its AES-GCM fallback were not touched
  by this audit; the self test exercises seal/open on both paths.
* UI, branding, features: unchanged. No screen was modified.

---

## 6. Remaining uncertainty

Stated plainly, because the request asked for it:

1. **The hash in the request does not match any build this repository produced.** Until that is
   resolved, it is unknown whether the VirusTotal report describes our binary at all.
2. **No re-scan has been done.** The detection may persist on the new binary; nobody can say it
   will not without uploading it.
3. **The installer is still unsigned.** SmartScreen will still warn. Everything else in this report
   improves the odds and reduces the reasons for a heuristic to fire, but signing is the only thing
   that removes the "unrecognised app" warning, and it needs a certificate this project does not
   have.
4. **The `.exe` is not byte-reproducible** (the jar is); different JDK and WiX versions produce
   different installers, so a hash comparison across builds is only meaningful for artifacts from
   the same workflow run.
5. **The Win32 code paths added here (`WinLinks`) cannot be executed in this environment** — there
   is no Windows host and no `java.lang.foreign` Win32 target available here. They are guarded by
   `WinLinks.supported` and fall back to copy, and CI compiles and runs on Windows, but the
   junction calls themselves have not been exercised against a real reparse point in this session.
   This is the one part of the fix that needs a manual test on Windows before release.
