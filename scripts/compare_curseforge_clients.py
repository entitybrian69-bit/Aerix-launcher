"""Run historical/current Java clients against the same public mirror, without keys.
Android helpers are stubbed; HTTPS/JSON/client code is real. This is not an Android test.
"""
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "net/kdt/pojavlaunch/modloaders/modpacks/api"
SOURCE = "app_pojavlauncher/src/main/java/" + PACKAGE
BASELINES = {"baseline_1_1_2": "1a12183", "release_1_3_3": "784c0a8", "current_1_3_4": "a7fd04b"}


def main():
    report = {}
    for label, revision in BASELINES.items():
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            files = {
                "android/util/ArrayMap.java": "package android.util; public class ArrayMap<K,V> extends java.util.HashMap<K,V> {}",
                "android/util/Log.java": "package android.util; public class Log { public static int d(String t,String s){return 0;} public static int w(String t,String s){return 0;} }",
                "net/kdt/pojavlaunch/BuildConfig.java": 'package net.kdt.pojavlaunch; public class BuildConfig {public static final String VERSION_NAME="1.3.4";}',
                "net/kdt/pojavlaunch/Tools.java": "package net.kdt.pojavlaunch; public class Tools { public static String read(java.io.InputStream in) throws java.io.IOException {return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);} }",
            }
            for name in ["ApiHandler.java"] + (["CurseForgeApiHandler.java"] if label == "current_1_3_4" else []):
                files[PACKAGE + "/" + name] = subprocess.check_output(
                    ["git", "show", revision + ":" + SOURCE + "/" + name], cwd=ROOT, text=True)
            client = 'new CurseForgeApiHandler(null)' if label == "current_1_3_4" else 'new ApiHandler("https://mod.mcimirror.top/curseforge/v1")'
            files[PACKAGE + "/CompareMain.java"] = '''package net.kdt.pojavlaunch.modloaders.modpacks.api;
import com.google.gson.*; import java.util.*;
public class CompareMain {
 public static void main(String[] args) {
  HashMap<String,Object> q=new HashMap<>();
  q.put("gameId",432); q.put("classId",6); q.put("searchFilter","");
  q.put("sortField",1); q.put("sortOrder","desc"); q.put("pageSize",30); q.put("index",0);
  JsonObject report=new JsonObject();
  try {
   JsonObject body=CLIENT.get("mods/search",q,JsonObject.class);
   JsonElement data=body==null?null:body.get("data");
   boolean valid=data!=null && data.isJsonArray();
   report.addProperty("valid_list",valid);
   if(valid) report.addProperty("count",data.getAsJsonArray().size());
  } catch(Exception failure) {report.addProperty("exception",failure.getClass().getSimpleName());}
  System.out.println(report);
 }
}'''.replace("CLIENT", client)
            for name, text in files.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
            gson = ROOT / "app_pojavlauncher/libs/gson-2.8.6.jar"
            subprocess.run(["javac", "-cp", str(gson), "-d", str(work)] + [str(work / n) for n in files], check=True, capture_output=True, timeout=30)
            try:
                result = subprocess.run(["java", "-Dsun.net.client.defaultConnectTimeout=8000",
                    "-Dsun.net.client.defaultReadTimeout=15000", "-cp", str(work) + ":" + str(gson),
                    PACKAGE.replace("/", ".") + ".CompareMain"], capture_output=True, text=True, timeout=40)
                report[label] = json.loads(result.stdout)
                # Never publish raw logs/bodies from historical clients.
                if "response code: 503" in result.stderr:
                    report[label]["http_status"] = 503
            except subprocess.TimeoutExpired:
                report[label] = {"result": "timed_out"}
    print("::notice title=Historical CurseForge clients::" + json.dumps(report, sort_keys=True))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
