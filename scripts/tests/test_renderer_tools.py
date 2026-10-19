import contextlib
import csv
import importlib.util
import io
import json
import pathlib
import tempfile
import types
import unittest
from unittest.mock import patch

SCRIPTS = pathlib.Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


bundle = load("prepare_rustgl_bundle")
benchmark = load("renderer_benchmark")
builder = load("build_rustgl_bundle")


class RendererToolsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        evidence = self.root / "synthetic-license.txt"
        evidence.write_text("Synthetic importer test fixture. No third-party grant is asserted.")
        self.inventory = self.root / "inventory.json"
        self.inventory.write_text(json.dumps({"sourceRevision": "a" * 40, "components": [{"name": "fixture", "version": "1"}]}))
        artifacts = []
        for abi, (bits, machine) in bundle.ABIS.items():
            file = self.root / "input" / abi / "librust_gl.so"
            file.parent.mkdir(parents=True)
            data = bytearray(64)
            data[:7] = b"\x7fELF" + bytes([bits, 1, 1])
            data[16:18] = (3).to_bytes(2, "little")
            data[18:20] = machine.to_bytes(2, "little")
            file.write_bytes(data)
            artifacts.append({"abi": abi, "name": file.name, "bytes": len(data), "sha256": bundle.digest(file)})
        self.audit = {"redistributionApproved": True, "reviewedBy": "SYNTHETIC TEST ONLY", "sourceRevision": "a" * 40,
                      "components": [{"name": "fixture", "version": "1", "licenseConclusion": "MIT",
                                      "evidence": [{"file": evidence.name, "sha256": bundle.digest(evidence)}]}], "artifacts": artifacts}
        self.approval = self.root / "approval.json"
        self.args = types.SimpleNamespace(approval=self.approval, inventory=self.inventory, version="0.3.0", input=self.root / "input", output=self.root / "output", readelf="mock-llvm-readelf")

    def run_import(self, details="LOAD 0x0 0x0 0x0 0x40 0x40 R E 0x4000"):
        self.approval.write_text(json.dumps(self.audit))
        with patch.object(bundle.subprocess, "check_output", return_value=details), contextlib.redirect_stdout(io.StringIO()):
            bundle.prepare(self.args)

    def test_audited_synthetic_import_builds_four_abi_index(self):
        self.run_import()
        index = json.loads((self.args.output / "index.json").read_text())
        self.assertEqual(4, len(index["releases"]))
        self.assertEqual("gles", index["backend"])
        self.assertTrue(all(len(r["files"]) == 2 for r in index["releases"]))

    def test_unknown_license_or_missing_approval_refused(self):
        self.audit["components"][0]["licenseConclusion"] = "UNREVIEWED"
        with self.assertRaises(ValueError):
            self.run_import()
        self.audit["components"][0]["licenseConclusion"] = "MIT"
        self.audit["redistributionApproved"] = False
        with self.assertRaises(ValueError):
            self.run_import()
        self.assertFalse(self.args.output.exists())

    def test_experimental_acceptance_does_not_claim_legal_clearance(self):
        self.audit["redistributionApproved"] = False
        self.audit["experimentalUserAuthorized"] = True
        self.args.experimental_mit_declaration = True
        self.run_import()
        index = json.loads((self.args.output / "index.json").read_text())
        self.assertFalse(index["redistributionApproved"])
        self.assertTrue(index["experimentalUserAuthorized"])

    def test_experimental_flag_without_acceptance_refused(self):
        self.audit["redistributionApproved"] = False
        self.args.experimental_mit_declaration = True
        with self.assertRaises(ValueError):
            self.run_import()

    def test_build_license_expressions_require_known_permissive_terms(self):
        self.assertTrue(builder.supported_license("(MIT OR Apache-2.0) AND Unicode-3.0"))
        self.assertFalse(builder.supported_license("MIT OR GPL-3.0"))
        self.assertFalse(builder.supported_license(None))

    def test_bad_artifact_hash_refused(self):
        self.audit["artifacts"][0]["sha256"] = "b" * 64
        with self.assertRaises(ValueError):
            self.run_import()
        self.assertFalse((self.args.output / "index.json").exists())

    def test_unresolved_native_dependency_refused(self):
        with self.assertRaises(ValueError):
            self.run_import("(NEEDED) Shared library: [untrusted.so]\nLOAD 0x0 0x0 0x0 0x40 0x40 R E 0x4000")

    def test_four_kib_and_writable_executable_segments_refused(self):
        for flags in ["R E 0x1000", "RWE 0x4000"]:
            with self.assertRaises(ValueError):
                self.run_import("LOAD 0x0 0x0 0x0 0x40 0x40 " + flags)

    def test_immutable_version_refused(self):
        self.run_import()
        with self.assertRaises(ValueError):
            self.run_import()

    def test_present_analysis_uses_game_intervals(self):
        file = self.root / "synthetic.csv"
        with file.open("w") as stream:
            writer = csv.writer(stream); writer.writerow(["present_ns"])
            for i in range(101):
                writer.writerow([i * 20_000_000])
        args = types.SimpleNamespace(frames=file, warmup_seconds=0, label="SYNTHETIC, NOT PHONE DATA")
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            benchmark.analyze(args)
        data = json.loads(output.getvalue())
        self.assertEqual(50, data["fps"])
        self.assertEqual(20, data["p99FrameMs"])

    def test_duplicate_frame_timestamps_refused(self):
        file = self.root / "bad.csv"
        file.write_text("present_ns\n1\n1\n2\n")
        with self.assertRaises(ValueError):
            benchmark.analyze(types.SimpleNamespace(frames=file, warmup_seconds=0, label="synthetic"))
