from pathlib import Path
base = Path(__file__).resolve().parents[1] / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/aerix'
screen = (base / 'ui/screens/InstanceEditorScreen.kt').read_text()
bridge = (base / 'bridge/PojavContentBridge.kt').read_text()
optimizer = (base / 'resourcepack/ResourcePackOptimizer.java').read_text()
scaler = (base / 'resourcepack/AndroidPackImageScaler.kt').read_text()
for text in ('Make smaller pack copy', 'ResourcePackOptimizer.createCopy(target.file, AndroidPackImageScaler)',
             'turn off the original and select this copy', 'The original stays untouched'):
    assert text in screen, text
assert 'names.contains(name + ".mcmeta")' in optimizer
assert 'hasLegacyAnimations' in optimizer and 'candidate.createNewFile()' in optimizer
assert 'BitmapFactory.Options().apply { inJustDecodeBounds = true }' in bridge
assert 'inSampleSize = sample' in scaler
print('Resource pack repair UI wiring and safeguards passed')
