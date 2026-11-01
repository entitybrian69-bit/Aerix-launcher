import contextlib
import importlib.util
import io
import os
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("check_curseforge", Path(__file__).parents[1] / "check_curseforge.py")
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class CurseForgeLiveCheckTest(unittest.TestCase):
    def run_check(self, results, key=""):
        output = io.StringIO()
        with patch.dict(os.environ, {"CURSEFORGE_API_KEY": key}, clear=True), \
                patch.object(check, "probe", side_effect=results) as probe, \
                contextlib.redirect_stdout(output):
            try:
                check.main()
                failed = False
            except SystemExit as error:
                self.assertEqual(error.code, 1)
                failed = True
        return failed, output.getvalue(), probe.call_args_list

    def test_mirror_outage_without_key_is_failure(self):
        failed, output, calls = self.run_check([{"result": "http_error", "status": 503}] * 2)
        self.assertTrue(failed)
        self.assertIn('"build_key_present": false', output)
        self.assertTrue(all(call.args[1] is None for call in calls))

    def test_minimal_search_success_does_not_validate_discover(self):
        failed, _, _ = self.run_check([{"result": "http_error", "status": 400}, {"result": "valid_list"}])
        self.assertTrue(failed)

    def test_official_success_passes_without_disclosing_or_forwarding_key(self):
        failed, output, calls = self.run_check([
            {"result": "valid_list"}, {"result": "http_error", "status": 503},
            {"result": "http_error", "status": 503}], key="private-test-key")
        self.assertFalse(failed)
        self.assertNotIn("private-test-key", output)
        self.assertEqual(calls[0].args[1], "private-test-key")
        self.assertTrue(all(call.args[1] is None for call in calls[1:]))

    def test_exact_mirror_success_passes_with_no_key(self):
        failed, _, _ = self.run_check([{"result": "valid_list"}] * 2, key=" DUMMY ")
        self.assertFalse(failed)


if __name__ == "__main__":
    unittest.main()
