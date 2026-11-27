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
| Windows build of the new packaging | **CI only** — no Windows host here |
| Signing with a real certificate | **Not done** — no certificate exists; the path is wired and fails closed |
| Launch, auth, mod install, uninstall on Windows | **Not tested here**; CI compiles, self-tests and runs the shipped jar under the bundled runtime |
| Re-scan | **Not done** — needs the user to upload the new artefact |
