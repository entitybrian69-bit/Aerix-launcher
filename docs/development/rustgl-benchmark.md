# RustGL phone benchmark protocol

**Measurement status: NOT RUN ON HARDWARE.** This environment has no attached
Android phone. Unit-test timestamp fixtures are synthetic and cannot establish FPS,
thermal behavior, Vulkan capability, gameplay compatibility or a speedup.

## Device matrix

Use exact device/OS/build/GPU driver IDs in each report, rather than treating a GPU
family or marketing name as a capability guarantee.

| Coverage | Suggested real hardware | Required comparison |
|---|---|---|
| Older Qualcomm | Snapdragon 865 / Adreno 650, Android 11–13 | MobileGlues or OpenLTW vs RustGL GLES; system Vulkan capability report |
| Newer Qualcomm | Snapdragon 8 Gen 1 and 8 Gen 2 / Adreno 730/740, Android 13–16 | Same baseline/GLES; Vulkan only after complete native implementation and qualification |
| Midrange Arm | Helio G-series / Mali-G52 class | GLES correctness, memory pressure, thermal and unsupported-feature fallback |
| Newer Arm | Exynos 2100 / Mali-G78 or Dimensity 9000 / G710; Tensor G3 / G715 | Driver-specific shader/texture correctness, sustained performance |
| Unsupported/low-end | Android 8/API26 device with Vulkan 1.0 or no Vulkan | Probe failure and GLES fallback; never advertise Vulkan capability |
| ABI/page-size coverage | Real 32-bit process, x86_64 emulator for packaging only, Android 15+ 16 KiB device | ELF loading/rollback correctness; emulator is not a phone GPU benchmark |

Separate system-driver and custom-driver results. A probe of system Vulkan does
not qualify a different Turnip driver. Record launcher APK hash, bundle hash/revision,
Java runtime, Minecraft/loader/mod versions, flags, driver tuple, display refresh rate,
internal resolution, render/simulation distance, heap and graphics options.

## Workloads and controls

1. Use a new **dedicated** benchmark instance; copy a known world and checksum its
   archive. Do not alter the user's normal instance, mods, saves or accounts.
2. Test vanilla 1.16.5, 1.21.4 and a separately pinned supported Fabric/Sodium build;
   include GUI/text, entities, translucent water, FBOs, texture uploads and chunk rebuilds.
   Shader packs are a separate correctness workload, not an assumed supported feature.
3. Fix camera route and seed; retain a video/checkpoint description. Do not compare
   unrelated worlds or let background chunk generation differ between arms.
4. Unplug at 40–80% battery, disable battery saver, hold ambient at 23±2 C, same
   brightness/refresh rate, no recording/overlay/cloud downloads. Do not force clocks.
5. Cool until thermal service reports NONE and temperature is stable for five minutes;
   report unavailable vendor thermal data explicitly. Battery temperature is not SoC temperature.
6. Warm up for 180 seconds; measure 900 seconds. Run at least three paired repetitions
   in randomized ABBA/BAAB order, cooling between runs. Compare cold shader cache and
   warm shader cache separately. Use identical VSync/FPS caps (or uncapped on both).
7. Vulkan-vs-baseline arm is **BLOCKED** until the native renderer is complete and
   rendering-correct. An extension list or successful vkCreateInstance is insufficient.

## Collection and metrics

```sh
python3 scripts/renderer_benchmark.py collect \
  --serial PHONE_SERIAL --label adreno650-baseline-r1 \
  --seconds 900 --output /path/outside/git/adreno650-baseline-r1
# Repeat with the same workload/settings for RustGL GLES and paired baseline runs.
```

The script captures properties, thermal/battery/memory reports and a bounded
Perfetto trace. OEMs can deny sources; inspect the raw evidence, do not silently
substitute UI FPS. For FrameTimeline on Android 12+, also enable
`android.surfaceflinger.frametimeline` in a full Perfetto config when available:
the simple category-mode trace alone may not contain that source. For Android 8–11,
use a verified game-swap instrumentation stream or a supported presentation profiler.
The current GLFW counter measures swaps only and does not provide per-frame timestamps;
Android Choreographer callbacks alone are **not** a valid substitute.

Identify the actual game surface and correlate application surface tokens to display
presentation in Perfetto; do not combine launcher UI/IME/overlay frames. Export a CSV
with one column `present_ns`, strictly increasing actual game presentation timestamps.
Keep extraction method/query, surface name, dropped-frame treatment and the raw trace.
Then:

```sh
python3 scripts/renderer_benchmark.py analyze --frames verified-game-present.csv \
  --label adreno650-rustgl-gles-r1 --warmup-seconds 180 > metrics.json
```

If capture starts after warm-up, use `--warmup-seconds 0`. The analyzer calculates
interval-derived FPS, median/p95/p99 frame time, 1%-tail FPS (reciprocal of mean of
slowest 1% frame intervals), and >50/>100 ms intervals. These definitions are explicit;
never label the reciprocal of p99 as an independently measured "1% low". Also report
RSS/PSS/native/Java memory, CPU/GPU frequency/utilization where available, temperature
and thermal transitions over time, startup time, shader compilation counts/time,
crashes, GL/Vulkan errors and screenshots against baseline. Report paired-run spread
and sustained final-five-minute behavior, not just a peak FPS number.

## Optimization only after evidence

| Measured bottleneck / evidence | Candidate experiment | Acceptance / regression check |
|---|---|---|
| Shader translation dominates cold-load trace | Content-addressed shader cache keyed on source+flags+backend+driver+translator revision; atomic writes | Lower compilation p95/startup; identical shader output; invalidation/corrupt-cache tests |
| Excess GL state calls dominate CPU samples | Validated state shadowing/batching in native GL bridge | Fewer driver calls/CPU time; no state leakage, FBO or uniform regressions |
| Upload stalls/fence waits | Reusable staging/PBO ring with bounded memory and correct sync | Lower p99 without corruption or unbounded RSS; lifecycle/device-loss tests |
| GPU-bound frame or bandwidth | Profile attachment formats, overdraw, redundant transitions | Same resolution/visual output and lower GPU frame time; no hidden quality reduction |
| Sustained thermal collapse | Reduce proven CPU work/allocation/polling; tune thread counts by device evidence | Better sustained paired-run result at equal ambient/power state, not just first-minute FPS |

No native optimization is justified by current measurements because there are none.
The upstream already specifies opt-level=3/thin LTO; changing compiler flags without
measured correctness/performance results is not "maximum optimization".
