#!/usr/bin/env python3
"""Guard the right-rail Jar Execution tab and Mojo/Pojav AWT execution wiring."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / 'app_pojavlauncher/src/main'
ui = main / 'java/net/kdt/pojavlaunch/aerix/ui'

rail = (ui / 'AerixNavigationRail.kt').read_text()
nav = (ui / 'AerixNav.kt').read_text()
shell = (ui / 'AerixLauncherScreen.kt').read_text()
screen = (ui / 'screens/JarExecutionScreen.kt').read_text()
navigator = (main / 'java/net/kdt/pojavlaunch/aerix/bridge/PojavNavigator.kt').read_text()
awt = (main / 'java/net/kdt/pojavlaunch/awt/AWTActivity.java').read_text()

assert 'JAR_EXECUTION' in rail
utility_rail = rail.split('fun AerixHomeUtilityRail(', 1)[1]
assert '"Jar Execution"' in utility_rail and 'LauncherSection.JAR_EXECUTION' in utility_rail
assert 'LauncherSection.JAR_EXECUTION' in nav
assert 'LauncherSection.JAR_EXECUTION -> JarExecutionScreen(activity, navigate)' in shell

for token in (
    'OpenDocumentWithExtension("jar")',
    'PojavNavigator.canRunJar',
    'PojavNavigator.runJar',
    'PojavNavigator.inspectJar',
    'MultiRTUtils.getRuntimes()',
    'LauncherPreferences.PREF_JAVA_SANDBOX',
    'PojavSettingsBridge.writeBoolean(activity, "java_sandbox"',
    'openLogOutput',
    'trusted',
    'javaArgs',
):
    assert token in screen, token

for token in (
    'Intent(context, AWTActivity::class.java)',
    'putExtra("modUri", uri)',
    'putStringArrayListExtra("javaArgs"',
    'putExtra("trusted", trusted)',
    'putExtra("openLogOutput", openLogOutput)',
    'putExtra("runtimeName", runtimeName)',
    'ClassVersionReader.readClassMajorVersion',
    'fun parseJvmArgs(',
):
    assert token in navigator, token

assert 'mPreferredRuntimeName = extras.getString("runtimeName")' in awt
assert 'if (selectedRuntime == null) {\n            return;\n        }' in awt
assert 'JavaRunner.startJvm(' in awt

print('Jar Execution right-rail tab and Mojo/Pojav AWT wiring: smoke checks passed')
