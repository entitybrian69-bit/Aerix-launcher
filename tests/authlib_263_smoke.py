#!/usr/bin/env python3
"""Exercise the bundled agent's 26.3-era hooks against an isolated mock authlib/service.

This is a JVM integration test, not an Android or real-Minecraft gameplay test.
"""
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
component = root / 'app_pojavlauncher/src/main/assets/components/authlib-injector'
agent = component / 'authlib-injector.jar'
assert hashlib.sha256(agent.read_bytes()).hexdigest() == '9c7f4343e6c82034958ffb48c14a2cb0c85928be7283103ce17da00c6d5a7b10'
assert (component / 'version').read_text() == '128'  # Forces existing installs to unpack the new agent.
with ZipFile(agent) as jar:
    manifest = jar.read('META-INF/MANIFEST.MF').decode()
    assert 'Implementation-Version: 1.2.8' in manifest
    assert 'Premain-Class: moe.yushi.authlibinjector.Premain' in manifest
    assert 'moe/yushi/authlibinjector/httpd/DiscoveryFilter.class' in jar.namelist()
    assert jar.testzip() is None

UUID = '11111111111111111111111111111111'
class SkinServer(BaseHTTPRequestHandler):
    paths = []
    def log_message(self, *_): pass
    def do_GET(self):
        self.paths.append(self.path)
        if self.path.startswith('/sessionserver/session/minecraft/profile/' + UUID):
            value = base64.b64encode(json.dumps({'profileId': UUID, 'profileName': 'TestPlayer', 'textures': {'SKIN': {'url': 'http://127.0.0.1/skin.png'}}}).encode()).decode()
            payload = json.dumps({'id': UUID, 'name': 'TestPlayer', 'properties': [{'name':'textures', 'value':value}]}).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        else:
            self.send_response(404); self.end_headers()

with tempfile.TemporaryDirectory() as temp:
    temp = Path(temp)
    sources = {
        'com/mojang/authlib/services/MinecraftServicesDiscoveryService.java': '''
package com.mojang.authlib.services;
public class MinecraftServicesDiscoveryService {
    public boolean isAllowedTextureDomain(String url) { return false; }
    public static String discoveryUrl() { return "https://discovery.minecraftservices.com/minecraft/client"; }
}
''',
        'com/mojang/authlib/yggdrasil/TextureUrlChecker.java': '''
package com.mojang.authlib.yggdrasil;
public class TextureUrlChecker { public static boolean isAllowedTextureDomain(String url) { return false; } }
''',
        'AgentSmoke.java': '''
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.yggdrasil.TextureUrlChecker;
public class AgentSmoke {
    static String get(String url) throws Exception {
        try (InputStream in = new URL(url).openStream()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }
    public static void main(String[] args) throws Exception {
        String tex = "http://127.0.0.1/skin.png";
        if (!new MinecraftServicesDiscoveryService().isAllowedTextureDomain(tex)) throw new AssertionError("modern texture hook failed");
        if (!TextureUrlChecker.isAllowedTextureDomain(tex)) throw new AssertionError("legacy texture hook failed");
        if (new MinecraftServicesDiscoveryService().isAllowedTextureDomain("https://evil.example/skin.png")) throw new AssertionError("arbitrary domain allowed");
        String endpoint = MinecraftServicesDiscoveryService.discoveryUrl();
        if (!endpoint.startsWith("http://127.0.0.1:")) throw new AssertionError("discovery not redirected: " + endpoint);
        String discovery = get(endpoint).replace((char)92 + "/", "/");
        Matcher matcher = Pattern.compile("http://127\\\\.0\\\\.0\\\\.1:[0-9]+/https/sessionserver\\\\.mojang\\\\.com/session/minecraft/profile/\\\\{profileId\\\\}").matcher(discovery);
        if (!matcher.find()) throw new AssertionError("no redirected profile endpoint: " + discovery);
        String profile = get(matcher.group().replace("{profileId}", "11111111111111111111111111111111") + "?unsigned=false");
        if (!profile.contains("TestPlayer") || !profile.contains("textures")) throw new AssertionError("profile not received: " + profile);
        System.out.println("Mock 26.3 discovery, profile and texture hooks passed");
    }
}
'''
    }
    for name, source in sources.items():
        path = temp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    subprocess.run(['javac', '-d', str(temp), *[str(temp / p) for p in sources]], check=True)
    server = ThreadingHTTPServer(('127.0.0.1', 0), SkinServer)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    metadata = base64.b64encode(json.dumps({'skinDomains':['127.0.0.1', 'localhost', '.minecraft.net', '.mojang.com'], 'meta':{'serverName':'Aerix test', 'feature.no_mojang_namespace':True}}).encode()).decode()
    try:
        subprocess.run(['java', '-Dauthlibinjector.yggdrasil.prefetched=' + metadata,
            f'-javaagent:{agent}=http://127.0.0.1:{server.server_port}', '-cp', str(temp), 'AgentSmoke'],
            check=True, timeout=45)
        assert any(path.startswith('/sessionserver/session/minecraft/profile/' + UUID) for path in SkinServer.paths), SkinServer.paths
    finally:
        server.shutdown(); server.server_close(); thread.join()
