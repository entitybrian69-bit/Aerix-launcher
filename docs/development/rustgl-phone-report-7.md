# Exact uploaded RustGL replacement: phone failure / latest (7).log

The user reports the replacement still crashes. This is a failed device outcome;
CI success must not be presented as working gameplay. Do not switch back to our
source-built renderer without permission, or modify the uploaded native bytes.

## What this report establishes

- MC 1.16.5 / Fabric 0.19.5, aarch64; active plugin tag is the bundled RustGL tag.
- Native-load record identifies bundle 0.3.3, private verified ARM64 library path,
  namespaceBypass=false, GLES=true, major=3, provider loaded=true.
- RustGL reports system `/system/lib64/libEGL.so`, `libGLESv3.so`, live driver
  **Mali-G57 MC2 (ARM)**, GLES 3.2, 104 driver extensions and 27 compatibility
  extensions. This is evidence of live context/capability initialization, not
  evidence of successful presentation or gameplay. Do not reuse the older LTW /
  PowerVR / ARMv7 crash report as this attempt's native stack.
- Hundreds of unresolved-name/no-op warnings appear during GL binding lookup.
  That does not establish that any listed function was actually invoked, nor
  that the last listed function caused the failure. The renderer's own claim
  that "the process survives" is not a memory-safety guarantee.
- There is no fatal exception or native fault stack identifying the crashing PC.
- The diagnostic mirror exhausted its 64-KiB budget and left a partial prefix.
  The apparent file ending is therefore a capture limit, NOT a reliable crash
  location. This was a limitation in our diagnostic implementation.

The shared raw log contains account/session configuration and is intentionally
not copied into Git. These observations avoid that user-specific information.

## Capture revision 2

Keep all four user-provided native libraries and their pinned hashes unchanged.
Modify the launcher-side mirror only:

- First four unresolved-name/no-op warnings retained, then one omission notice.
  Repeated lookup chatter no longer fills the diagnostic log.
- Ordinary records capped at 48 KiB; remaining 16 KiB reserved for recognized
  fatal summaries and a bounded, whitelisted native-frame window.
- Account, command-line, environment and arbitrary following stdout are not
  included merely because they occur after a fatal header.
- Budget complete records rather than three separate prefix/body/newline writes.
  Optional diagnostics still fail closed after partial I/O errors.
- Local ASan/UBSan regressions reproduce 1,000 lookup warnings, late errors,
  native-fatal summary/frame capture, sensitive-line exclusion and late fatal
  capture after ordinary-budget exhaustion. Existing file/bounds/I/O tests and
  20 Python tools tests also pass.

This improves the next capture. It is NOT a renderer crash fix or a full crash
recorder: abrupt native death can lose pipe output, Android-only logcat faults
may not appear, and Minecraft appenders can overwrite/rotate mirrored tails.
No signal handlers are replaced. No userdata/mods/worlds/renderers are removed.
