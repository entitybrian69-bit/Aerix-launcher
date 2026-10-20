# User-uploaded RustGL APK: static inspection

The upload was named `app-debug.log` solely to pass the upload filter. Its contents
are a 2,376,934-byte APK, SHA256
`4ce0d57776132e0d782836d9b5ba7da57b0781b1f15f2eccf0766b239595e73f`.
Neither the APK nor its native libraries were executed, installed, committed, or
substituted into Aerix. Raw extracts remain inspection scratch files outside Git.

## Findings

- Manifest: package `dev.rustrenderer.plugin`, version 0.3.0/code 23, min API26,
  target API34, debuggable, extracted native libraries. Contains four real ELF ABIs.
- Resource string 0 contains the FCL V2 metadata preserved in
  `uploaded-rustgl-v2-metadata.json`. The `fclPlugin_V2` resource is 0x7f010000;
  the legacy `fclPlugin` marker is also present. GL and EGL use the same library;
  backend GLES; no additional preload libraries. Defaults match the established
  Aerix integration, including blank Mesa overrides and LWJGL NoChecks=true.
  No missing preload/integration requirement was discovered here.
- The library hashes differ from both our unpatched pinned-source 0.3.0 build and
  patched bundles. This proves different build bytes, **not** a newer source
  revision, authenticity or a working phone build.
- All libraries have separate read/write and executable LOAD segments and depend
  on libc, libdl, liblog and libm. Inspected arm64/x86_64 segments align to 16 KiB;
  armeabi-v7a/x86 align to 4 KiB, below our importer's uniform 16 KiB policy.
- APK has a V2 signing block and an Android Debug certificate. Certificate extraction
  is **not** cryptographic signature verification or trusted-upstream provenance.
  No LICENSE/NOTICE files were present among its nine ZIP entries. Previous
  experimental README-MIT acceptance is not independent clearance for an unknown
  prebuilt's dependencies. Do not bypass approval/checksum/alignment checks.

## Confirmed memory-safety defect in the uploaded binary

ARM64 `glGetStringi`, symbol VA `0xd1888`, has the old temporary-vector lifetime bug:

1. `0xd18b0`: call returns the extension vector into the stack result.
2. `0xd18d0`: loads the selected string pointer into callee-saved `x19`.
3. `0xd1948`–`0xd196c`: loops over the vector elements and frees their allocations.
4. `0xd19f4`: frees the outer vector storage.
5. `0xd19f8`: moves the retained, now dangling string pointer from `x19` to return `x0`.

The calls at `0xd1968` and `0xd19f4` target PLT VA `0x15fb40`:
`adrp x16,0x16a000; ldr x17,[x16,#0xd98]; ...; br x17`.
`readelf -rW` identifies GOT `0x16ad98` as `free@LIBC`.
This is direct static evidence of freeing the returned string's allocation, not
an inference from version text or different hashes.

The x86_64 library independently shows the same pattern: `glGetStringi` VA
`0xd0d80`, string pointer retained in `rbx` at `0xd0db5`, loop frees at `0xd0e45`,
vector free at `0xd0e54`, dangling return at `0xd0e77`. GOT `0x16adb8` is `free@LIBC`.

Blindly replacing the patched bundle with this prebuilt would reintroduce that
fault. It **does not** establish the cause of the user's unresolved Android crash
on patched 0.3.1, nor demonstrate that the plugin cannot sometimes launch. No phone,
performance, vulnerability-exploitation, or full malware-scan result is claimed.

## Decision

Keep the existing checked bundle and diagnostics intact. Do not publish another
"fixed" APK merely by copying these bytes. The next useful distinction is whether
this **exact original plugin has actually launched Minecraft on the same phone**,
and in which launcher; that is currently unknown. The upload itself supplies no
trusted source-revision mapping or device-success evidence.
