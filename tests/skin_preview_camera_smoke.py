"""Structural checks for viewport-fitting and mobile skin-preview rendering controls."""
from pathlib import Path
import math
import unittest

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app_pojavlauncher/src/main/assets/skinview"
APP = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix"


class SkinPreviewCameraSmoke(unittest.TestCase):
    def test_camera_fit_uses_both_viewport_axes_and_a_model_margin(self):
        js = (ASSETS / "skinview.js").read_text()
        self.assertIn("function minimumCameraDistance()", js)
        self.assertIn("getWidth() / Math.max(1, getHeight())", js)
        self.assertIn("Math.min(verticalHalfFov, horizontalHalfFov)", js)
        self.assertIn("PLAYER_BOUNDING_RADIUS / Math.sin(limitingHalfFov) * CAMERA_FIT_MARGIN", js)
        self.assertIn("skinViewer.controls.minDistance = fitDistance * 0.98", js)
        self.assertIn("configureCameraLimits()", js)
        self.assertIn("const fitDistance = configureCameraLimits()", js)
        for width, height in ((900, 500), (360, 740), (240, 600)):
            aspect = width / height
            vertical_half_fov = math.radians(50 / 2)
            horizontal_half_fov = math.atan(math.tan(vertical_half_fov) * aspect)
            limiting_half_fov = min(vertical_half_fov, horizontal_half_fov)
            distance = 18.75 / math.sin(limiting_half_fov) * 1.08
            self.assertGreaterEqual(distance * math.sin(limiting_half_fov), 18.75)

    def test_preview_pixel_budget_and_wardrobe_controls(self):
        js = (ASSETS / "skinview.js").read_text()
        self.assertIn("MAX_RENDER_PIXELS = 1_200_000", js)
        self.assertIn("MAX_PREVIEW_PIXEL_RATIO = 1.25", js)
        self.assertIn("function previewPixelRatio", js)
        self.assertIn("skinViewer.controls.enableZoom = interactive", js)
        self.assertIn("skinViewer.controls.enableDamping = interactive", js)
        self.assertIn('skinViewer.controls.addEventListener("start"', js)
        self.assertIn('skinViewer.controls.addEventListener("end"', js)
        self.assertIn("autoRotateResumeTimeout", js)

    def test_pointer_camera_updates_are_conflated_and_frame_synchronized(self):
        component = (APP / "components/AerixPlayerSkin.kt").read_text()
        self.assertIn("Channel<Triple<Int, Int, Int>>(Channel.CONFLATED)", component)
        self.assertIn("withFrameNanos { }", component)
        self.assertNotIn("delay(16)", component)
        self.assertIn("autoRotate = true", (APP / "ui/screens/TrinkiesWardrobeScreen.kt").read_text())

    def test_reported_preview_callers_use_fitted_distance_and_clipping_boundaries(self):
        home = (APP / "ui/screens/HomeScreen.kt").read_text()
        wardrobe = (APP / "ui/screens/TrinkiesWardrobeScreen.kt").read_text()
        self.assertIn("cameraDistance = 48", home)
        self.assertIn(".clipToBounds()", home)
        self.assertIn("cameraDistance = 48", wardrobe)
        self.assertIn(".clip(RoundedCornerShape(0.dp))", wardrobe)


if __name__ == "__main__":
    unittest.main()
