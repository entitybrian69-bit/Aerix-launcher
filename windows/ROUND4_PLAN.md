# Round 4 plan — from evidence, before any edits

## What the new report actually tells us

| Signal | Old report | This report | Reading |
|---|---|---|---|
| Detection | Malicious.moderate.ml.score | Suspicious.Low.ML.Score | Same engine, **weaker verdict**. |
| Size | hash matched nothing | 45.28 MB = **47,475,200 B exactly** | This scan is of **our** build (07bf7a5). |
| Ratio | — | 1/67 | One ML engine; 66 silent. |

## The five characteristics, tested against the code

| VT characteristic | Found? | Evidence |
|---|---|---|
| Self-delete | **No** | 0 hits for DELAY_UNTIL_REBOOT, `/c del`, self-removal. Every delete targets a download, .part, log, trashed instance, or failed hash. |
| Detects debug environment | **No** | 0 hits for IsDebuggerPresent / CheckRemoteDebugger / NtQueryInformationProcess / OutputDebugString. No jdwp agent. |
| Checks USB bus | **No** | 0 device-enumeration APIs. The "usb" byte hits are getSuccess/StatusBadge noise. |
| Obfuscated | **No** | No UPX, no ProGuard/R8, no string encryption, no custom loader. |
| PE executable | **Yes** | A jpackage exe installer. |

Four of five do not appear in our code. They are properties of the **jpackage exe stub**:

- self-extracting PE: 45 MB stub = 12 MB jar + compressed 48 MB runtime
- extracts to temp at runtime — structurally what a dropper does
- unsigned — no reputation to weigh

## What we will and will not do

Will not: UPX, obfuscation, anti-analysis, polymorphism, AV exclusions, hash-chasing.
Will: ship a recommended distribution that is **not** an opaque self-extracting PE —
a portable app-image ZIP with a real javaw.exe, signed.

## Changes

1. build.sh
   - portable ZIP from the jpackage app-image on Windows (real AerixLauncher.exe + runtime beside it)
   - --win-menu --win-shortcut --win-shortcut-prompt --win-dir-chooser --win-upgrade-uuid
   - sign every shipped PE (installer and app-image exe), not just the stub
   - verify the signature after signing; fail the build if it does not check out
   - RFC 3161 timestamp alongside the legacy one
2. workflow: publish the app-image ZIP, keep the installer, keep hashes in the log
3. SelfTest: pin the rules
4. docs: audit section, root cause, Trapmine FP submission

## Not testable here

No Windows host, no certificate. Cannot build, sign, launch or rescan the .exe here. CI builds it;
the scan needs the user. No clean-result claim will be made.
