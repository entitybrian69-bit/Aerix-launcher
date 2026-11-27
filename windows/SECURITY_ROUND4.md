# Round 4 — the Trapmine detection, tested against the code

This round had better evidence than the previous ones, and the evidence changed what could be
concluded.

## 1. The scanned file is ours this time

The report says the file is **45.28 MB**. `AerixLauncher-2.4.4.exe` from commit `07bf7a5` is
**47,475,200 bytes**, which is **45.28 MiB** — the same number to two decimal places. So unlike the
first report, whose hash matched nothing this repository produced, this scan describes our binary.

Two things follow: the findings below are about real code, and the earlier rounds' fixes had a
measurable effect — the verdict weakened from `Malicious.Moderate.ML.Score` to
`Suspicious.Low.ML.Score`, the lowest label this engine gives.

## 2. Each characteristic, tested

The report lists five file characteristics. They were each searched for in the source *and* in the
compiled bytecode, because a static model reads the binary, not the source.

| Characteristic | In our code? | What the search actually found |
|---|---|---|
| **PE executable** | **Yes** | It is a Windows PE. |
| **Self-delete** | No | No `MoveFileEx(..., MOVEFILE_DELAY_UNTIL_REBOOT)`, no `/c del`, no self-removal. Every `deleteIfExists` call removes a download, a `.part` file, a rotated log, an instance the user asked to trash, or a file that failed its hash — none touches the launcher or its installation. |
| **Detects debug environment** | No | No `IsDebuggerPresent`, `CheckRemoteDebuggerPresent`, `NtQueryInformationProcess` or `OutputDebugString`. No `-agentlib:jdwp`, no `-Xrunjdwp`. The `SandboxId: RETAIL` string in the Xbox auth payload is a Microsoft API field, not a sandbox check. |
| **Checks USB bus** | No | No setupapi, no device interfaces, no `CM_Get_Device*`, no `GetLogicalDrives` loop. The only three byte matches for `usb` in 4,000-odd classes are inside `getSuccess` and `StatusBadge`. |
| **Obfuscated** | No | No UPX, no ProGuard/R8, no string encryption, no custom class loader, no runtime section unpacking. The jar is a plain ZIP of ordinary `.class` files and the sources are public. |

Four of the five do not exist in the launcher.

## 3. Root cause, as far as it can be established

The characteristics that *were* reported are properties of the **packaging**, not of the code.

`AerixLauncher-2.4.4.exe` is a jpackage **exe installer**. That format is a PE stub containing a
compressed copy of the application: our 12 MB jar plus a 48 MB Java runtime image. When it runs, it
extracts all of that to a temporary directory, starts a child process and cleans up afterwards.

Strip the labels off that description and it is: *a large unsigned Windows executable that unpacks
an embedded runtime into a temp folder and launches a process from it.* That is a textbook dropper
pattern, and it is exactly what a static ML model is trained to score. The model's own wording —
`Suspicious`, `Low` — is consistent with "the shape resembles a dropper, nothing else in the file
supports the view". Two of the other reported characteristics (self-delete, obfuscated) are also
properties people associate with self-extracting stubs.

This is a hypothesis that fits every observation. It is not a proof: the model's features are not
published, so no one outside Trapmine can say which feature contributed what. What can be said is
that the behaviours that can be checked were checked, and the ones people fear are not present.

## 4. What was changed

The response is not to hide the shape. It is to stop shipping the shape as the recommended
download, and to give the PE a reputation.

1. **The recommended download is no longer a self-extracting PE.** `build.sh` now packages the
   portable build from the jpackage **app image**: a plain folder containing a real
   `AerixLauncher.exe` beside the runtime it uses. No stub, no extraction at startup, no temp
   directory, nothing to clean up. The installer stays, unchanged in function.
2. **The installer can now upgrade in place.** `--win-upgrade-uuid` (pinned, because changing it
   strands every existing installation) plus the standard `--win-menu`, `--win-shortcut`,
   `--win-shortcut-prompt` and `--win-dir-chooser` switches. Previously every release installed
   *beside* the previous one, leaving a second 48 MB runtime and a second entry in Add/Remove
   Programs.
3. **Every PE we ship is signed and the signature is verified.** Signing used to cover the installer
   only, and the result was never checked. A new `sign_pe` helper signs the installer *and* the
   app-image launcher, then runs `osslsigncode verify` and **fails the build** if the signature does
   not check out. Timestamps are requested as both RFC 3161 (`-ts`) and legacy Authenticode (`-t`).
4. **The rules are pinned by a self-test** so nothing above can quietly regress.

No packer, obfuscator, string encryption, anti-analysis check or hash-chasing change was made. No
feature was removed.

## 5. What this does and does not change about the verdict

It does not falsify the previous verdict, and it does not guarantee a new one. What it does is
remove the specific structural property that most plausibly produced a dropper-shaped score, and
give the binary an identity a model or SmartScreen can weigh.

**A re-scan is the only thing that can tell us whether it worked.** Until that is done, this
document claims no clean result — as before, and for the same reason.

## 6. False-positive submission

If the next scan still returns `Suspicious.Low.ML.Score`, this is the material for a submission to
Trapmine. Fill in the hash of the build that was scanned.

```
Product:      Aerix Launcher for Windows
Version:      2.4.4
Vendor:       luciferstudios (open source, MIT)
Source:       https://github.com/luciferstudios/Aerix-launcherPc
Download:     https://github.com/luciferstudios/Aerix-launcherPc/releases/tag/desktop-2.4.4
SHA-256:      <hash of the scanned file>
Signed:       yes/no — see the release notes

Verdict:      Suspicious.Low.ML.Score (single engine, 1/67)

Why this is a false positive:

1. The file is an open-source Java desktop application packaged with the JDK's
   own jpackage tool. The full source, and the build script that produces the
   binary, are public and reproducible.

2. The four behaviours commonly associated with this class of verdict are not
   present in the code, and can be checked:
   - no self-deletion of the executable or its installation
   - no debugger detection (no IsDebuggerPresent and no equivalent)
   - no USB or removable-device enumeration
   - no packing, obfuscation or encrypted strings; the jar is a plain ZIP of
     .class files

3. The PE in question is a jpackage installer stub. Its "extracts an embedded
   runtime to a temporary directory and starts a child process" behaviour is how
   jpackage installers work by design, for every Java application that uses it.
   This is a property of the packaging tool, not of the application.

4. The launcher makes no network connections except to Mojang, Microsoft, the
   Modrinth and CurseForge APIs, the mod loader Mavens, OptiFine and GitHub, and
   every downloaded file is verified against a published hash before use.

5. An alternative distribution with no self-extraction is published alongside
   the installer (the portable build), and it carries the same verdict/(no
   verdict) pattern.
```

## 7. Verification state, stated plainly

| Item | Status |
|---|---|
| Source audit of the five characteristics | **Done** — source and bytecode |
| `build.sh` changes | Done; `bash -n` clean; signing helper exercised against a stub signer for all four paths (no cert, missing tool, bad signature, good signature) |
| Self-tests | 23/23 here, including the new packaging checks, each validated by removing the guard |
| Windows build of the new packaging | **CI only** — no Windows host here; the first two attempts failed and their failures are recorded in §8 |
| Signing with a real certificate | **Not done** — no certificate exists; the path is wired and fails closed |
| Launch, auth, mod install, uninstall on Windows | **Not tested here**; CI compiles, self-tests and runs the shipped jar under the bundled runtime |
| Re-scan | **Not done** — needs the user to upload the new artefact |

## 8. Two failures worth recording

The packaging change went out and failed twice before it produced a portable build. Both failures are
in the report rather than being quietly fixed, because in each case the useful output was not the fix
but the guard that now makes the mistake impossible to repeat.

**First attempt: nothing was packaged.** jpackage deletes its staging app image the moment it writes
`AerixLauncher-2.4.4.exe`. The portable step ran after the installer and looked for that image, found
nothing, and its `if [[ -d ... ]]` simply did not fire. The build reported success and the release
gained no portable build at all. Fix: build the app image explicitly, before the installer.

**Second attempt: the order was still wrong.** `make_app_image` was called before
`make_runtime_image`, so `dist/runtime` did not exist when jpackage ran, no `--runtime-image` was
passed, and the step failed. Fix: build the runtime first, then the app image, then the installer —
which is the dependency order, not an arbitrary one.

What changed as a result is not just the ordering. Three checks now exist that would have caught both:

- CI fails if `dist/AerixLauncher-*-windows-portable.zip` is missing, if it contains no
  `AerixLauncher.exe`, or if it does not bundle `runtime/bin/javaw.exe`.
- `make_app_image` refuses to run without a runtime image instead of building one that needs a
  system Java.
- The self-test asserts that `make_runtime_image` appears before `make_app_image` in the script,
  and was confirmed to fail when the two lines are swapped back.

A release that silently ships less than it claims is the failure mode this round was supposed to
eliminate, so it is worth being clear that it happened here.

**A third failure, and this one is a real bug in the launcher.** With the build order fixed, the CI
self-test failed on a different check with:

```
ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0
    at java.base/java.util.ArrayList.add(ArrayList.java:484)
    at net.kdt.aerix.desktop.core.instance.Instances.onChange(Instance.kt:295)
    at net.kdt.aerix.desktop.ui.screens.HomeScreen.<init>(HomeScreen.kt:75)
```

`Instances` and `Accounts` kept their change listeners in a plain `ArrayList` while `Log` and
`GameLauncher` already used `CopyOnWriteArrayList`. Listeners are registered as screens are
constructed; `notifyChanged()` runs on whichever thread made the change, which is normally an IO
thread. Two threads in `ArrayList` and the array underneath is being resized while another thread
reads it.

This is not a packaging issue and it is not a false positive — it is a genuine concurrency bug that
could throw in the field, most likely at exactly the moment a screen is first opened. Both stores now
use `CopyOnWriteArrayList`, and `notifyChanged()` iterates directly instead of taking a `toList()`
copy, which was the unsafe operation. `Widgets` keeps its `ArrayList` because it is Swing-only and
therefore confined to the event thread.

The self-test gained a check that registers and fires listeners from four threads and asserts no
notifications are lost, plus a source check that the two stores use the concurrent type. Both were
confirmed to fail when the `ArrayList` is put back.

## 9. The verdict, restated

The security work in this round is real: four of five reported characteristics are absent from the
code with evidence, the packaging no longer ships a self-extracting stub as the recommended download,
signing now covers every PE and is verified, and the audit turned up a genuine concurrency bug that
had nothing to do with antivirus.

What has *not* happened is a new scan. Nothing here establishes that Trapmine will stop flagging the
build, and a clean result must not be reported until someone uploads the new artefact and reads the
result. The false-positive submission in §6 is ready for the case where it does not.

## 10. The release that finally shipped

The build that produced the current release is `11e610a`. Its release assets, verified by querying
the release rather than by trusting a log:

| Asset | Size | Uploaded |
|---|---|---|
| `AerixLauncher-2.4.4-windows-portable.zip` | 45,822,438 B | 2026-11-27 14:20:32Z |
| `AerixLauncher-2.4.4.exe` | 47,744,000 B | 2026-11-27 14:20:32Z |
| `AerixLauncher-2.4.4-portable.zip` | 11,608,438 B | 2026-11-27 14:20:31Z |
| `SHA256SUMS.txt` | 403 B | 2026-11-27 14:20:30Z |

The app-image portable build exists and contains a real `AerixLauncher.exe` beside the bundled
runtime — CI asserts both before publishing. The installer job's last step still reported failure,
but the failure was in its own verification, not in the upload: `gh` exited 4 (not authenticated) on
a `gh release view` that ran inside the *log-publishing* step where `$TAG` was never defined, and
`set -u` turned that into a hard error after the assets were already attached. The check has been
moved into the step that owns the tag and now prints what is attached; the log-push step warns
instead of dying, because a silent failure there is what made the log look stale while the release
was fine.

**What is still missing from this report: the SHA-256 values.** The build sandbox cannot reach GitHub
release assets or Actions artifacts (every request to `release-assets.githubusercontent.com` and
`productionresultssa*.blob.core.windows.net` ends in `EOF`), and the run's own log/artifact
downloads are blocked the same way. Rather than copy a hash from a stale log, the table above records
only what the API actually reports. The hashes for this build should be read from the published
`SHA256SUMS.txt`, or the build re-run and the checksums copied from the "Assert the installers exist"
step.

## 11. Correction to §10, and the numbers

§10 was written from the release API while the job had already reported failure. It recorded the
upload as successful *and* left the hashes missing. Both were wrong, and the run has since been read
properly. This section replaces the table in §10.

**Build `7ce0971` — assets on the release:**

| Asset | Size | SHA-256 |
|---|---|---|
| `AerixLauncher-2.4.4.exe` | 47,735,808 B | `b0dfbfa4e5dbbb402ba0b444dfa38a2b0ec2c1fd7c31db842d14bc64647f0edf` |
| `AerixLauncher-2.4.4-windows-portable.zip` | 45,815,241 B | `6f8a2bf253bb2a512c8b60e4baa3d4e606492804ab84ff3656877c7ce2069882` |
| `AerixLauncher-2.4.4-portable.zip` | 11,608,438 B | `855dcef74adb28ce256a1674a4c24941677cf1f3275cc6267a9033924529eb19` |

These come from the build's own `SHA256SUMS.txt` and the same hashes appear in the CI log, so the
published artifacts are the ones the build produced.

**Why the step still said failure.** Three overlapping globs: `dist/AerixLauncher-*-portable.zip` also
matches `AerixLauncher-2.4.4-windows-portable.zip`, so that 45 MB file was passed to `gh release
upload` twice. The first upload succeeded; the second hit the same asset name, returned
`HTTP 404: Not Found`, and `gh` reported the whole step as failed. Every asset was in fact attached
before the error. The same overlap put the windows-portable filename in `SHA256SUMS.txt` twice, which
is what gave it away.

The publish and checksum steps now deduplicate by resolved path, and the publish step verifies what
is attached and prints it.

**What this means for the audit.** This was the fourth distinct packaging failure in this round, and
each one was a case of the build claiming something it had not verified. The pattern is worth stating
plainly: every failure was found by a check, none by reading the code and believing it. The launcher
binaries themselves have now been built and their hashes recorded, which is the first point in this
round where the VirusTotal work can actually proceed.
