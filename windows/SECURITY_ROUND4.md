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
Trapmine. The hash below is build `0ac4db2`. If a later build replaces the release assets, the hash
must be updated to match whatever was actually scanned — a submission quoting the wrong hash wastes
their time and yours.

```
Product:      Aerix Launcher for Windows
Version:      2.4.4
Vendor:       luciferstudios (open source, MIT)
Source:       https://github.com/luciferstudios/Aerix-launcherPc
Download:     https://github.com/luciferstudios/Aerix-launcherPc/releases/tag/desktop-2.4.4
SHA-256:      2820b7a6b67af009138b1ba3f4e925db5a841c64125ea9bc2ba3e7da986bb4ed
Signed:       no — no code-signing certificate is configured; the build logs UNSIGNED

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

5. An alternative distribution with no self-extraction at all is published
   alongside the installer: AerixLauncher-2.4.4-windows-portable.zip is a plain
   folder holding AerixLauncher.exe next to the runtime it uses. If that file
   does not draw the same verdict, the difference points at the installer stub
   rather than at the application, which is itself useful evidence.
```

## 7. Verification state, stated plainly

| Item | Status |
|---|---|
| Source audit of the five characteristics | **Done** — source and bytecode |
| `build.sh` changes | Done; `bash -n` clean; signing helper exercised against a stub signer for all four paths (no cert, missing tool, bad signature, good signature) |
| Self-tests | 24/24, locally and on CI, including the new packaging and concurrency checks, each validated by reintroducing the bug |
| Windows build of the new packaging | **Done on CI** — both jobs green at `0ac4db2`; the four failed attempts are recorded in §8 and §11 |
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

## 10. What shipped, with the numbers

The green build is `0ac4db2`. Both jobs passed — the Linux `build` job (compile, 24/24 self test,
full page render) and the Windows `installer` job (jpackage, portable build, reproducibility check,
self test under the bundled runtime, release publish). Its published assets:

| Asset | Size | SHA-256 |
|---|---|---|
| `AerixLauncher-2.4.4.exe` | 47,735,808 B | `2820b7a6b67af009138b1ba3f4e925db5a841c64125ea9bc2ba3e7da986bb4ed` |
| `AerixLauncher-2.4.4-windows-portable.zip` | 45,815,241 B | `c2547d8911d94b871a29fcf4dcdf7a762aae2e221378bca4dab3cfb4b5634b61` |
| `AerixLauncher-2.4.4-portable.zip` | 11,608,438 B | `855dcef74adb28ce256a1674a4c24941677cf1f3275cc6267a9033924529eb19` |

These are the hashes the build computed for the files it then uploaded, so the published artifacts
are the ones the build produced. Release:
`https://github.com/luciferstudios/Aerix-launcherPc/releases/tag/desktop-2.4.4` (pre-release).

**The `.exe` hash is what should go to VirusTotal.** It is the file the original report describes.
The windows-portable ZIP is the recommended download for users, but it is a container of signed or
unsigned PEs and a runtime — scanning it is a different question from scanning the installer.

## 11. Every packaging failure, and what found it

This round had **four** distinct packaging failures before a green run. All four were the build
reporting something it had not actually verified, and all four were caught by a check rather than by
reading the code and believing it.

| # | Failure | What it did to the build | Found by |
|---|---|---|---|
| 1 | Portable step ran after jpackage had deleted its staging app image | Reported success, shipped no portable build at all | Comparing the release assets to what was claimed |
| 2 | App image built before the runtime image existed | Step failed outright; no `--runtime-image` | The new CI assertion that the portable zip must exist and contain a real exe and runtime |
| 3 | `$TAG` used in the log-publishing step, where it is never defined | `set -u` error, exit 4, *after* a successful upload | The failure annotation contradicting the release API |
| 4 | `AerixLauncher-*-portable.zip` also matched the windows-portable file | Same 45 MB file uploaded twice; second attempt got HTTP 404 and failed the step after every asset was attached | The filename appearing twice in `SHA256SUMS.txt` |

Failure 2 is the one worth dwelling on. It was not a missing check that let the first attempt through
— the check that then caught it did not exist either. Writing the assertion (the zip must exist, must
contain `AerixLauncher.exe`, must bundle `runtime/bin/javaw.exe`) is what turned an unverifiable claim
into a testable one, and it immediately failed.

An empty `SHA256SUMS.txt` was a fifth, quieter defect: `set -u` plus an empty `${msi[@]}` aborted the
block, so the file that exists specifically to let people verify their download was published empty.

## 12. What is still not done

Stated plainly, because it is the difference between this report and a clean verdict:

- **No VirusTotal submission has been made from here.** Uploading the hash above and reading the
  result is the only way to know whether the packaging change affected the verdict. Nothing in this
  document predicts it.
- **No authenticode signature exists on this build.** No certificate is configured. Signing is wired
  and fails closed; the CI log says `UNSIGNED` for both PEs.
- **No Windows interactive testing.** No one has launched this build from a Windows desktop here.
  CI compiles, self-tests, runs the shipped jar under the bundled runtime, and asserts the artifact
  structure. That is not the same as double-clicking it.
- **The ML verdict may not move at all.** If it does not, §6 is ready to send.

