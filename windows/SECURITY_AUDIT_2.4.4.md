# Aerix Launcher for Windows 2.4.4 — Security Audit

**Scope:** `windows/aerix-desktop` (47 Kotlin files, ~16.3k lines), the packaging path
(`build.sh`, `windows-desktop.yml`) and every runtime artefact shipped for Windows.
**Target:** Aerix Launcher 2.4.4, the portable ZIP and the jpackage `.exe`.
**Question:** is the VirusTotal detection (Trapmine `Malicious.moderate.ml.score`) a true positive?

---

## 0. Read this first: which binary was scanned

Three things about this investigation need to be stated plainly before any conclusion.

**a) The hash in the request does not match any binary this repository produced.**
Since this document was first written the installer has been rebuilt twice — once to shrink the
bundled runtime, and once for the download-verification fixes in section 2.5 — and the published
`.exe` is now **47,475,200 bytes** (section 5.1). None of those builds matches the request's hash
either. The hash quoted in the request is
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
| Running Forge/NeoForge/OptiFine installer jars | These loaders only ship installers; this is how every launcher installs them. HTTPS alone was not enough, so the jars are now hash-verified or pinned — see 2.5.2. |
| Update check against GitHub | One GET a day for release metadata. It never downloads or executes an update; it shows a link. |
| `raw.githubusercontent.com` for server banners | Image URLs from a public catalogue, loaded as pictures. |
| Executing `java` from `PATH` | `JavaRuntimes` scans well-known locations, `JAVA_HOME`, `PATH` and the player's explicit choice; the player can override it in Settings. This is inherent to a launcher. |

### 2.5 Downloaded files were trusted on the strength of their URL — **fixed**

HTTPS tells you who you are talking to. It does not tell you that the bytes you received are the
bytes the upstream published, and it does not protect a file once it is on disk. Three code paths
opened a downloaded file and trusted it. All three are the kind of thing that a heuristic engine is
right to be suspicious of, and all three were ordinary bugs rather than deliberate design.

#### 2.5.1 Java runtimes, verified only when a checksum happened to be present

**Where:** `core/game/JavaRuntimes.kt`

The Adoptium API returns a `checksum` field for most releases. The code was written as
`if (release.sha256.isNotBlank()) { …verify… }`, so when the field was empty the archive was
unpacked and then **executed** with no verification at all. A missing field is not evidence the
file is safe, and this is the single highest-value target in the launcher: a Java runtime is an
interpreter that the launcher then runs arbitrary game code under.

**Fix:** the check is unconditional. A blank checksum, or a failure to compute the hash, deletes the
archive and refuses the install with a log line naming the runtime. There is no path from "no
checksum available" to "install anyway".

#### 2.5.2 Loader installer jars were executed with no integrity check

**Where:** `core/loaders/Loaders.kt`

Forge, NeoForge, OptiFine and Better than Adventure are all installed by downloading an installer
jar and executing it. Nothing verified those jars, and a cached jar left in `installers/` was reused
on the strength of its file size alone — so anything that could write to that folder could plant a
jar the launcher would later run.

**Fix, in two parts:**

- **Where upstream publishes a hash, it is now checked.** Forge and NeoForge publish a `.sha1`
  sidecar next to every artefact on their Maven; the launcher fetches it and verifies the jar before
  execution. A mismatch deletes the jar and aborts.
- **Where upstream publishes no hash, the file is pinned.** OptiFine and Better than Adventure
  publish no checksum. The jar is hashed at download time, the hash is written next to it, and every
  later reuse — including a cached jar — must match that pin. A jar modified on disk after download
  is rejected rather than executed. This is not as strong as an upstream signature and the log says
  so, but it closes the "plant a file in `installers/`" path.

#### 2.5.3 Modpack archives were unpacked without checking their hash

**Where:** `ui/screens/DiscoverScreen.kt`, `core/content/ContentInstaller.kt`, `core/content/Content.kt`

The Discover page wrote the `.mrpack`/CurseForge archive straight to disk and unpacked it. Modrinth
publishes a SHA-1 for every version and it was already being parsed into `Content.Version.sha1` — it
was simply not passed to the download. CurseForge versions were hard-coded to `sha1 = null`.

**Fix:** a single `downloadVerified(...)` helper now owns the download-write-verify-cleanup sequence,
and both archive paths go through it. CurseForge version metadata now carries the hash the API
already returns (`algo 1 = SHA-1`, confirmed against the CurseForge OpenAPI contract). Inside a pack,
each file entry must carry a hash, as the mrpack format requires; an entry without one is reported
and skipped instead of being installed on trust, and `sha512` is honoured where present. The
CurseForge pack-file path verifies against the file endpoint's `hashes` array.

#### 2.5.4 What did *not* change

Skins and server banners are still fetched without hashes. They are decoded as images and never
executed, so a hash would add nothing a decoder does not already have to survive; the skin URL,
which comes out of the on-disk account store, is now restricted to HTTPS so it cannot be downgraded
in transit. Image decoders are the one place here where the input is genuinely untrusted, and that
risk is unchanged by this round.

#### 2.5.5 Evidence that the rules hold

Three self-tests, one behavioural and two source-level, run on every `--test` and `--selftest`:

- A real loopback HTTP server serves a fixed body; `downloadVerified` must accept the correct hash,
  must refuse a wrong one, and must leave neither the file nor a `.part` behind when it refuses.
- The Java runtime guard must still delete the archive and abort; the Forge/NeoForge path must
  still assign its expected hash from the sidecar.

Both source assertions were validated by deliberately re-introducing each bug and confirming the
suite goes red. That mattered: the first version of the assertion matched the *helper's definition*
rather than its *call site*, and passed with the bug restored. The version that ships matches the
call site.

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
and fails if the hashes differ. (After the section 2.5 changes the jar hash is
`12fb94f450bb6804e2e85df8918c5177d48aac4d288a43fc66140aeb87b06cce`, unchanged across two clean
builds here.)

**The scope of that claim matters.** It means *same toolchain, same bytes* — a given commit built
twice on the same JDK produces the identical jar. It does **not** mean the jar is the same across
different compilers: a jar built here with the Kotlin 2.4.21 compiler running on JDK 25 does not
match the CI jar built with the same compiler on Temurin 21, even though both target Java 17
bytecode. Reproduce a hash with the JDK named in section 7.1, or take the hash from the CI log for
the run that produced the artefact you have.

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
| 8 | Java runtimes must have a checksum, or the install is refused | Guard asserted in the self test; the assertion was **verified to fail** with the guard removed |
| 9 | Loader installers checked against their Maven `.sha1`; unhashed ones pinned | Call site asserted in the self test; **verified to fail** when the sidecar fetch was stubbed out |
| 10 | Pack archives verified before unpacking (Modrinth **and** CurseForge) | Loopback-server test proves a wrong hash is refused and leaves nothing on disk |
| 11 | Pack entries must carry a hash; `sha512` honoured | Assertion pins the skip; a malformed pack is reported, not installed |
| 12 | Skin URLs restricted to HTTPS | Source-level fix; the non-HTTPS path logs and is not fetched |

**Test state:** 22 checks, 22 passed, 0 failed — run on Linux here and on the Windows runner.
The suite is in `ui/shell/SelfTest.kt`; `./build.sh --selftest` fails the build on any failure.

### 5.1 Current artefacts

Built by the `Windows desktop launcher` workflow on `windows-latest`, after all of the above:

| File | Size | SHA-256 |
|---|---|---|
| `AerixLauncher-2.4.4.exe` | 47,475,200 | `c547ccb7520055a41346f24fb533254252aa88d08ae470d160629ee2e97edc0b` |
| `AerixLauncher-2.4.4-portable.zip` | 11,605,690 | `53883ad5e79625f7ebcc09efc25398634adc08f9a0da8a24278185045d5402f3` |

These are the artefacts from workflow run `45858781499` on commit `07bf7a5`, which contains the
download-verification fixes in section 2.5. They replace the earlier build (`…130ab57a`), whose
hashes are kept in the repository history and in the release notes of the superseded build. Sizes
went up slightly because the verification code added classes to the jar; the runtime module list is
unchanged.

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

---

## 7. Rebuilding this yourself

Everything below runs from `windows/aerix-desktop`. The sources are plain Kotlin compiled by
`kotlinc`; there is no Gradle, and no build tool that downloads anything at build time.

### 7.1 What you need

| Requirement | Version used here | Notes |
|---|---|---|
| JDK | Temurin 21 (any 17+) | `JAVA_HOME` must point at it. `jpackage`/`jlink` are needed only for the installer. |
| Kotlin compiler | 2.4.21 | `KOTLINC` may point at the `kotlinc` binary; otherwise `PATH` is used. |
| WiX Toolset | 3.x | Only for `--dist` producing an `.exe`; the CI image has it. |

### 7.2 Commands

```bash
export JAVA_HOME=/path/to/jdk
export KOTLINC=/path/to/kotlinc        # optional

./build.sh --test        # compile + run the self test, fail the build on any failure
./build.sh --selftest    # run the self test against an existing build
./build.sh --dist        # jar + jlink runtime + installer (.exe) on Windows, portable zip anywhere
```

Outputs: `build/aerix-launcher.jar`, `dist/AerixLauncher/` (app image), `dist/runtime/` (jlink),
`dist/AerixLauncher-2.4.4.exe` on Windows, and `dist/AerixLauncher-2.4.4-portable.zip` everywhere.

### 7.3 Verifying a build without trusting me

```bash
# 1. The jar is byte-reproducible: build twice and compare.
./build.sh --dist && sha256sum build/aerix-launcher.jar
./build.sh --dist && sha256sum build/aerix-launcher.jar     # must match

# 2. The self test must report 0 failures, including the verification checks in 2.5.5.
./build.sh --selftest

# 3. The installer's own file list, without installing it.
dist/AerixLauncher-2.4.4.exe          # double-click; do not run with /S in a script
```

The `.exe` is **not** byte-reproducible (see 6.4); compare hashes only within a single CI run.

---

## 8. Final security checklist

Each row is something the audit request asked to be checked. "Where" points at the evidence, so a
reader can confirm it rather than take it on trust.

| Check | Result | Where |
|---|---|---|
| Suspicious APIs / libraries | None. No third-party libraries at all; only the JDK and Win32 calls via `java.lang.foreign`. | 1.1 |
| Dependency count | Zero. No Gradle, no npm, no bundled `jar`-in-`jar`. | 1.1, 5 |
| Process injection | Not present. No `VirtualAllocEx`, no thread injection, no remote-process writes. | 1.2 |
| Persistence | None. No Run key, no startup folder, no scheduled task, no service. | 1.4 |
| Hidden background execution | None. Background threads are downloads, skin fetches and the update check, all visible in the log and in the UI; each stops when the launcher closes. | 1.2, 1.3 |
| Privilege escalation | Not requested. No manifest `requireAdministrator`, no UAC prompt from the app. The installer elevates because it writes to `Program Files`, which is ordinary for an installer. | 3 |
| Suspicious PowerShell / CMD | No `powershell`, `cmd /c`, `wscript`, `mshta`, `certutil`, `bitsadmin` anywhere. The last `cmd /c mklink` was removed in this audit. | 2.1, 5 |
| Unexplained network traffic | None. Every host is either Mojang/Microsoft (game, auth), the Modrinth or CurseForge API, the loader's own Maven, OptiFine, or GitHub for the update check. No telemetry, no analytics. | 1.3 |
| Unnecessary file / registry writes | None. Writes are the game directory, the instance store, the log, and a skin cache. No registry writes at all. | 1.4 |
| Obfuscation / packing | None. No UPX, no ProGuard/R8, no string encryption, no custom class loader. Sources ship uncompiled in the repo and the jar is a plain zip of `.class` files. | 2.4, 5 |
| App metadata / version info | `Implementation-Title`, `Implementation-Version` set in the manifest; installer carries the product name, version, publisher and icon. | 3.3, 5 |
| All downloaded files verified | **Now yes.** Runtime archives, loader installers, pack archives and pack entries are verified or pinned before use. Images are decoded, never executed. | 2.5 |
| HTTPS | All endpoints are HTTPS. Redirects are `NORMAL`, so HTTPS never downgrades. The one plain-HTTP string was a dead template and is now `https`. | 1.3, 2.5.4 |
| Input validation | Instance names and file names are sanitised; pack paths are checked against directory escape (`safeResolve`). | 2.1, 2.2 |
| Windows permissions | No admin manifest on the app. Writes stay under `%APPDATA%` and the chosen game directory. | 1.4, 3 |
| Transparent background processes | Every process the launcher starts is either the game, a loader installer, `taskkill` on the game it started, or the browser for login. All are visible and logged. | 1.2 |
| Features preserved | None removed. The only behavioural change is that a corrupt or tampered download now fails loudly instead of installing silently. | 5.2 |

### 8.1 What this checklist does not say

It does not say the binary is clean, and it must not be read that way. It says the specific
behaviours that heuristic engines key on were looked for, and where they were found they were fixed.
Whether any engine still flags the result is a question only a scan can answer — see section 6.
