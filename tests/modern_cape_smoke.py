"""Run the real cape bridge against named and intermediary Minecraft API fixtures."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MOD = ROOT / 'aerix_client_mod/src/main/java/aerix/client/mod/ModernCapeBridge.java'


def fixtures(named):
    if named:
        n = dict(id='resources.Identifier', asset='core.ClientAsset', skin='world.entity.player.PlayerSkin',
                 info='client.multiplayer.PlayerInfo', player='client.player.AbstractClientPlayer',
                 image='com.mojang.blaze3d.platform.NativeImage', texture='client.renderer.texture.AbstractTexture',
                 dynamic='client.renderer.texture.DynamicTexture', manager='client.renderer.texture.TextureManager',
                 mc='client.Minecraft', textureasset='Texture', resource='ResourceTexture', mkid='fromNamespaceAndPath',
                 body='body', model='model', secure='secure', lookup='skinLookup', getskin='getSkin',
                 getinfo='getPlayerInfo', playerfield='player', getmanager='getTextureManager', read='read',
                 copy='copyFrom', pixels='getPixels', upload='upload', gettexture='getTexture', register='register', release='release')
    else:
        n = dict(id='class_2960', asset='class_12079', skin='class_8685', info='class_640', player='class_742',
                 image='class_1011', texture='class_1044', dynamic='class_1043', manager='class_1060', mc='class_310',
                 textureasset='class_12081', resource='class_10726', mkid='method_60655', body='comp_1626',
                 model='comp_1629', secure='comp_1630', lookup='field_45607', getskin='method_52810',
                 getinfo='method_3123', playerfield='field_1724', getmanager='method_1531', read='method_4309',
                 copy='method_4317', pixels='method_4525', upload='method_4524', gettexture='method_4619',
                 register='method_4616', release='method_4615')
    for key in ('id', 'asset', 'skin', 'info', 'player', 'image', 'texture', 'dynamic', 'manager', 'mc'):
        if not (key == 'image' and named): n[key] = 'net.minecraft.' + n[key]
    n['textureType'] = n['asset'] + '.' + n['textureasset']
    n['assetType'] = n['asset'] + '.' + n['resource']
    sources = {}
    def add(key, text):
        fqcn = n[key]; package, cls = fqcn.rsplit('.', 1)
        sources[fqcn] = 'package ' + package + ';\n' + text.replace('$CLASS$', cls).format(**n)
    add('id', '''public class $CLASS$ {{ public final String value; public $CLASS$(String v) {{ value=v; }}
      public static $CLASS$ {mkid}(String n,String p) {{ return new $CLASS$(n+":"+p); }}
      public boolean equals(Object o) {{ return o instanceof $CLASS$ && value.equals((($CLASS$)o).value); }}
      public int hashCode() {{ return value.hashCode(); }} }}''')
    add('asset', '''public class $CLASS$ {{ public interface {textureasset} {{}}
      public static class {resource} implements {textureasset} {{ public final {id} id,path;
      public {resource}({id} i,{id} p) {{ id=i;path=p; }} }} }}''')
    add('skin', '''public class $CLASS$ {{ public final {textureType} b,c,e; public final String m; public final boolean s;
      public $CLASS$({textureType} b,{textureType} c,{textureType} e,String m,boolean s) {{ this.b=b;this.c=c;this.e=e;this.m=m;this.s=s; }}
      public {textureType} {body}() {{return b;}} public String {model}() {{return m;}} public boolean {secure}() {{return s;}} }}''')
    add('info', '''public class $CLASS$ {{ public java.util.function.Supplier<{skin}> {lookup};
      public final {skin} initial; public $CLASS$({skin} initial) {{this.initial=initial;}}
      public {skin} {getskin}() {{ if({lookup}==null) {lookup}=()->initial; return {lookup}.get(); }} }}''')
    add('player', '''public class $CLASS$ {{ public final {info} info; public $CLASS$({info} i) {{info=i;}}
      protected {info} {getinfo}() {{ return info; }} }}''')
    add('image', '''public class $CLASS$ implements AutoCloseable {{ public int value; public boolean closed;
      public static $CLASS$ {read}(java.io.InputStream in) throws Exception {{ $CLASS$ i=new $CLASS$(); i.value=in.read(); if(i.value<0)throw new Exception("bad frame");return i; }}
      public void {copy}($CLASS$ i) {{value=i.value;}} public void close() {{closed=true;}} }}''')
    add('texture', '''public class $CLASS$ implements AutoCloseable {{public boolean closed; public void close() {{closed=true;}} }}''')
    add('dynamic', '''public class $CLASS$ extends {texture} {{public {image} image; public int uploads;
      public $CLASS$(java.util.function.Supplier<String> label,{image} i) {{image=i;}}
      public {image} {pixels}() {{return image;}} public void {upload}() {{uploads++;}}
      @Override public void close() {{super.close();image.close();}} }}''')
    add('manager', '''public class $CLASS$ {{public java.util.Map<{id},{texture}> map=new java.util.HashMap<>();
      public {texture} {gettexture}({id} id) {{return map.get(id);}}
      public void {register}({id} id,{texture} tex) {{ {texture} old=map.put(id,tex);if(old!=null)old.close(); }}
      public void {release}({id} id) {{ {texture} t=map.remove(id);if(t!=null)t.close(); }} }}''')
    add('mc', '''public class $CLASS$ {{public {player} {playerfield}; public {manager} manager=new {manager}();
      public {manager} {getmanager}() {{return manager;}} }}''')
    suffix = 'Named' if named else 'Mapped'
    test = '''package aerix.client.mod;
      public class Test$SUFFIX$ {{
        static void check(boolean x, String why) {{if (!x) throw new AssertionError(why);}}
        public static void main(String[] args) throws Exception {{
          java.io.File dir=new java.io.File(args[0],"config/aerix_cape_frames");dir.mkdirs();
          java.nio.file.Files.write(new java.io.File(dir,"frame_00.png").toPath(),new byte[]{{11}});
          java.nio.file.Files.write(new java.io.File(dir,"frame_01.png").toPath(),new byte[]{{22}});
          java.io.File meta=new java.io.File(dir,"meta.properties"); java.nio.file.Files.write(meta.toPath(),new byte[]{{1}}); meta.setLastModified(100000);
          {id} bodyId={id}.{mkid}("minecraft","body"); {textureType} body=new {assetType}(bodyId,bodyId);
          {skin} original=new {skin}(body,null,null,"slim",true);
          {info} info=new {info}(original), remote=new {info}(original);
          {mc} mc=new {mc}(); mc.{playerfield}=new {player}(info);
          ModernCapeBridge bridge=new ModernCapeBridge(Test$SUFFIX$.class.getClassLoader(),$NAMED$);
          bridge.tick(mc,new java.io.File(args[0]));
          {skin} overridden=info.{getskin}();
          check(overridden!=original && overridden.b==body && overridden.m.equals("slim") && overridden.s,"preserve skin ["+bridge.status()+"]");
          check(overridden.c!=null && overridden.c==overridden.e,"cape and elytra");
          check(remote.{getskin}()==original,"remote player unchanged");
          {id} capeId={id}.{mkid}("aerix","local_cape");
          {dynamic} tex=({dynamic})mc.manager.{gettexture}(capeId);
          check(tex!=null && tex.image.value==11,"first frame");
          bridge.tick(mc,new java.io.File(args[0])); check(tex.image.value==22 && tex.uploads==1,"frame cycling");
          java.nio.file.Files.write(new java.io.File(dir,"frame_00.png").toPath(),new byte[]{{33}});meta.setLastModified(200000);
          bridge.tick(mc,new java.io.File(args[0]));
          bridge.tick(mc,new java.io.File(args[0]));check(tex.image.value==33,"updated cape");
          info.{lookup}=()->original;bridge.tick(mc,new java.io.File(args[0]));check(info.{getskin}().c!=null,"lookup replacement");
          mc.manager.{release}(capeId);bridge.tick(mc,new java.io.File(args[0]));
          check(mc.manager.{gettexture}(capeId)!=null,"texture reload");
          {manager} oldManager=mc.manager; mc.manager=new {manager}();
          bridge.tick(mc,new java.io.File(args[0]));
          check(oldManager.{gettexture}(capeId)==null && mc.manager.{gettexture}(capeId)!=null,"manager replacement");
          check(info.{getskin}().c!=null,"cape after manager replacement");
          mc.{playerfield}=null;bridge.tick(mc,new java.io.File(args[0]));
          check(info.{getskin}()==original && mc.manager.{gettexture}(capeId)==null,"disconnect restores lookup and releases texture");
          System.out.println("$SUFFIX$ cape bridge passed");
        }}
      }}'''.replace('$SUFFIX$', suffix).replace('$NAMED$', str(named).lower()).format(**n)
    sources['aerix.client.mod.Test' + suffix] = test
    return sources


def main():
    with tempfile.TemporaryDirectory(prefix='aerix-cape-test-') as temp:
        base = Path(temp); files = []
        for variant in (True, False):
            for fqcn, text in fixtures(variant).items():
                path = base / (fqcn.replace('.', '/') + '.java')
                path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text); files.append(str(path))
        classes = base / 'classes'; classes.mkdir()
        subprocess.run([os.environ.get('JAVAC', 'javac'), '-d', str(classes), str(MOD), *files], check=True)
        for variant in ('Named', 'Mapped'):
            game = base / variant; game.mkdir()
            subprocess.run([os.environ.get('JAVA', 'java'), '-cp', str(classes), 'aerix.client.mod.Test' + variant, str(game)], check=True)


if __name__ == '__main__': main()
