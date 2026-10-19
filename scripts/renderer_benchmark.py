#!/usr/bin/env python3
"""Collect phone evidence or analyze verified game-present timestamps. Never invent FPS."""
import argparse
import csv
import json
import math
import pathlib
import statistics
import subprocess
import time


def adb(serial, *command):
    return subprocess.check_output(["adb", "-s", serial, *command], text=True, stderr=subprocess.STDOUT, timeout=30)


def collect(args):
    output = args.output
    output.mkdir(parents=True, exist_ok=False)
    report = {"serial": args.serial, "label": args.label, "startedUnix": time.time(), "durationSeconds": args.seconds,
              "measurementStatus": "RAW_EVIDENCE_ONLY_NOT_A_GAME_FPS_RESULT"}
    commands = {"properties": ("getprop",), "thermal": ("dumpsys", "thermalservice"),
                "battery": ("dumpsys", "battery"), "memory": ("dumpsys", "meminfo", "net.kdt.pojavlaunch"),
                "display": ("dumpsys", "display")}
    for key, cmd in commands.items():
        try:
            report[key] = adb(args.serial, "shell", *cmd)
        except (subprocess.SubprocessError, OSError) as error:
            report[key] = {"unavailable": str(error)}
    (output / "before.json").write_text(json.dumps(report, indent=2))
    remote = "/data/local/tmp/aerix-benchmark.perfetto-trace"
    subprocess.run(["adb", "-s", args.serial, "shell", "perfetto", "-o", remote, "-t", f"{args.seconds}s",
                    "-b", "128mb", "sched", "freq", "idle", "am", "wm", "gfx", "view", "binder_driver", "hal"],
                   check=True, timeout=args.seconds + 90)
    subprocess.run(["adb", "-s", args.serial, "pull", remote, str(output / "phone.perfetto-trace")], check=True, timeout=120)
    after = {}
    for key in ["thermal", "battery", "memory"]:
        try:
            after[key] = adb(args.serial, "shell", *commands[key])
        except (subprocess.SubprocessError, OSError) as error:
            after[key] = {"unavailable": str(error)}
    (output / "after.json").write_text(json.dumps(after, indent=2))
    print("Raw trace collected. Verify FrameTimeline/game surface attribution before extracting present_ns CSV.")


def analyze(args):
    with args.frames.open() as stream:
        values = [int(row["present_ns"]) for row in csv.DictReader(stream)]
    if len(values) < 3 or any(b <= a for a, b in zip(values, values[1:])):
        raise ValueError("Need at least three strictly increasing, verified game-present timestamps")
    cutoff = values[0] + args.warmup_seconds * 1_000_000_000
    values = [value for value in values if value >= cutoff]
    if len(values) < 3:
        raise ValueError("Insufficient measured frames after warm-up")
    intervals = [(b - a) / 1e6 for a, b in zip(values, values[1:])]
    ordered = sorted(intervals)
    def percentile(p):
        return ordered[max(0, math.ceil(p * len(ordered)) - 1)]
    result = {"label": args.label, "source": str(args.frames), "frames": len(values),
              "durationSeconds": (values[-1] - values[0]) / 1e9,
              "fps": 1000 / statistics.mean(intervals), "medianFrameMs": statistics.median(intervals),
              "p95FrameMs": percentile(.95), "p99FrameMs": percentile(.99),
              "onePercentTailFps": 1000 / statistics.mean(ordered[-max(1, math.ceil(len(ordered) * .01)):]),
              "intervalsOver50ms": sum(t > 50 for t in intervals), "intervalsOver100ms": sum(t > 100 for t in intervals),
              "warmupSecondsDiscarded": args.warmup_seconds,
              "attributionWarning": "Valid only if caller verified game frame source; UI vsync/Choreographer alone is invalid"}
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(required=True)
    collect_parser = sub.add_parser("collect")
    collect_parser.add_argument("--serial", required=True)
    collect_parser.add_argument("--label", required=True)
    collect_parser.add_argument("--output", required=True, type=pathlib.Path)
    collect_parser.add_argument("--seconds", type=int, default=900, choices=range(1, 3601))
    collect_parser.set_defaults(run=collect)
    analyze_parser = sub.add_parser("analyze")
    analyze_parser.add_argument("--frames", required=True, type=pathlib.Path)
    analyze_parser.add_argument("--label", required=True)
    analyze_parser.add_argument("--warmup-seconds", type=int, default=180)
    analyze_parser.set_defaults(run=analyze)
    args = parser.parse_args()
    try:
        args.run(args)
    except (ValueError, KeyError, OSError, subprocess.SubprocessError) as error:
        parser.exit(1, f"Benchmark unavailable: {error}\n")
