"""Exercise the real GameRenderer selection/failure logic with Android/native test doubles."""
import os
from pathlib import Path
import shlex
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / 'app_pojavlauncher/src/main/java'
files = {
    'android/content/Context.java': 'package android.content; public class Context {}',
    'android/system/ErrnoException.java': 'package android.system; public class ErrnoException extends Exception {}',
    'android/system/Os.java': 'package android.system; public class Os { public static void setenv(String a,String b,boolean c) throws ErrnoException {} }',
    'android/util/Log.java': 'package android.util; public class Log { public static int e(String a,String b){return 0;} public static int i(String a,String b){return 0;} public static int w(String a,String b){return 0;} public static int w(String a,String b,Throwable t){return 0;} }',
    'net/kdt/pojavlaunch/Logger.java': 'package net.kdt.pojavlaunch; public class Logger { public static void appendToLog(String s){} }',
    'net/kdt/pojavlaunch/Tools.java': 'package net.kdt.pojavlaunch; import java.io.File; public class Tools { public static String DIR_DATA="/tmp", NATIVE_LIB_DIR="/tmp"; public static File DIR_CACHE=new File("/tmp"); }',
    'net/kdt/pojavlaunch/instances/Instance.java': 'package net.kdt.pojavlaunch.instances; public class Instance {}',
    'net/kdt/pojavlaunch/prefs/LauncherPreferences.java': 'package net.kdt.pojavlaunch.prefs; public class LauncherPreferences { public static boolean PREF_FREEDRENO_SYSMEM, PREF_UBWC_WORKAROUND; }',
    'git/artdeell/mojoexec/MojoExec.java': 'package git.artdeell.mojoexec; public class MojoExec { public static void setNativeLibraryDir(String p){} public static void setUseTurnip(boolean b){} }',
    'net/kdt/pojavlaunch/game/renderer/impl/Stub.java': '''package net.kdt.pojavlaunch.game.renderer.impl;
import android.content.Context; import java.util.Map; import net.kdt.pojavlaunch.game.renderer.RenderSpec;
public class Stub implements RenderSpec { public boolean compatibleDevice(Context c){return true;} public String name(){return tag();} public int displayName(){return 0;} public String tag(){return "stub";} public String library(){return "stub";} public void setupEnvironment(Context c,Map<String,String> m){} public boolean setupRenderer(){return true;} }''',
    'net/kdt/pojavlaunch/game/renderer/impl/GLESRenderSpec.java': '''package net.kdt.pojavlaunch.game.renderer.impl;
public class GLESRenderSpec extends Stub {
 public static class GL4ESRenderSpec extends Stub { public String tag(){return "opengles2";} }
 public static class LTWRenderSpec extends Stub {} public static class NGGL4ESRenderSpec extends Stub {}
 public static class MgRenderSpec extends Stub {} public static class SFPEWRenderSpec extends Stub {} }''',
    'net/kdt/pojavlaunch/game/renderer/impl/MesaRenderSpec.java': '''package net.kdt.pojavlaunch.game.renderer.impl;
public class MesaRenderSpec extends Stub { public static class ZinkRenderSpec extends Stub {} public static class FreedrenoRenderSpec extends Stub {} public static class ExtMesaRenderSpec extends Stub {} public static class LegacyZinkRenderSpec extends Stub {} }''',
    'net/kdt/pojavlaunch/game/renderer/impl/ProtonRenderSpec.java': 'package net.kdt.pojavlaunch.game.renderer.impl; public class ProtonRenderSpec extends Stub {}',
    'net/kdt/pojavlaunch/game/renderer/impl/EmesisRenderSpec.java': 'package net.kdt.pojavlaunch.game.renderer.impl; public class EmesisRenderSpec extends Stub { public static boolean available(){return false;} }',
    'net/kdt/pojavlaunch/aerix/bridge/AerixRendererPlugin.java': 'package net.kdt.pojavlaunch.aerix.bridge; public class AerixRendererPlugin extends net.kdt.pojavlaunch.game.renderer.impl.Stub {}',
    'RendererSmoke.java': '''import net.kdt.pojavlaunch.game.renderer.*; import net.kdt.pojavlaunch.game.renderer.impl.Stub;
public class RendererSmoke {
 static class Choice extends Stub { boolean ok; String gl; Choice(boolean ok, String gl){this.ok=ok;this.gl=gl;} public String name(){return "choice";} public String tag(){return "choice";} public boolean setupRenderer(){return ok;} public String glLibraryPath(){return gl;} }
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  GameRenderer strict = new GameRenderer("opengles2", true); Choice good=new Choice(true,"libGLMojo.so"); strict.setCurrentRenderer(good); check(strict.maybeSetupRenderer()); check(strict.getLaunchableRenderer()==good);
  Choice failed=new Choice(false,"libGLMojo.so"); strict.setCurrentRenderer(failed); check(!strict.maybeSetupRenderer()); check(strict.getCurrentRenderer()==failed);
  Choice missing=new Choice(true,""); strict.setCurrentRenderer(missing); check(strict.getLaunchableRenderer()==null); check(strict.getCurrentRenderer()==missing);
  GameRenderer global=new GameRenderer("opengles2",false); global.setCurrentRenderer(failed); check(global.maybeSetupRenderer()); check("opengles2".equals(global.getCurrentRenderer().tag()));
  System.out.println("Instance renderer selection smoke test passed");
 } }'''
}
with tempfile.TemporaryDirectory() as tmp:
    tmp = Path(tmp)
    for name, content in files.items():
        target = tmp / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)
    paths = [str(p) for p in tmp.rglob('*.java')]
    paths += [str(src / p) for p in ('net/kdt/pojavlaunch/game/renderer/RenderSpec.java', 'net/kdt/pojavlaunch/game/renderer/GameRenderer.java', 'net/kdt/pojavlaunch/game/renderer/def/Renderers.java')]
    subprocess.run(shlex.split(os.environ.get('JAVAC', 'javac')) + ['-source', '8', '-target', '8', '-d', str(tmp), *paths], check=True)
    subprocess.run(shlex.split(os.environ.get('JAVA', 'java')) + ['-cp', str(tmp), 'RendererSmoke'], check=True)

# Check the cross-process handoff and the two easy-to-regress override paths as well.
activity = (src / 'net/kdt/pojavlaunch/game/GameActivity.java').read_text()
listener = (src / 'net/kdt/pojavlaunch/lifecycle/ContextAwareDoneListener.java').read_text()
runner = (src / 'net/kdt/pojavlaunch/utils/jre/GameRunner.java').read_text()
gles = (src / 'net/kdt/pojavlaunch/game/renderer/impl/GLESRenderSpec.java').read_text()
assert 'mainIntent.putExtra(GameActivity.INTENT_INSTANCE_ID, mInstanceId)' in listener
assert 'mainIntent.putExtra(GameActivity.INTENT_GLOBAL_RENDERER, mGlobalRenderer)' in listener
assert 'Instances.loadInstance(instanceId)' in activity
assert 'Instance instance = Instances.loadSelectedInstance()' not in activity
assert 'instanceRenderer ? instance.renderer' in activity
assert '!versionInfo.disableRendererChecks && !Tools.isValidString(instance.renderer)' in runner
assert 'instance.renderer = renderer.tag()' not in runner
assert 'gameRenderer.setupEnvironment(activity, instance)' in runner
assert 'Instances.loadSelectedInstance()' not in gles
print('Instance launch handoff wiring smoke test passed')
