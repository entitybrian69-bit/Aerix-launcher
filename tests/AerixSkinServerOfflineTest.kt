import android.content.Context
import net.kdt.pojavlaunch.aerix.bridge.AerixSkinServer
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.authenticator.AuthType
import net.kdt.pojavlaunch.authenticator.accounts.Account
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.File

private val uuid = PojavAccountBridge.offlineUuid("Alex").toString().replace("-", "")
private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
private var lookupCount = 0
private var lookupName = "Alex"
private var redirects = false

private class FakeConnection(url: URL) : HttpURLConnection(url) {
    private val body = ByteArrayOutputStream()
    override fun disconnect() {}
    override fun usingProxy() = false
    override fun connect() {}
    override fun getOutputStream() = body
    override fun getResponseCode() = if (redirects && url.path.contains("social-media")) 302 else 200
    override fun getInputStream(): ByteArrayInputStream {
        val content = if (url.path.contains("rpc/aerix_lookup_game_texture")) {
            lookupCount++
            """[{"game_name":"$lookupName","offline_uuid":"$uuid","slim":true,"skin_url":"https://lktupxfszfefsoyksfkg.supabase.co/storage/v1/object/public/social-media/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/game-skin-slim-1.png","cape_url":null}]""".toByteArray()
        } else png
        return ByteArrayInputStream(content)
    }
    override fun getContentLength() = -1
}

fun main() {
    URL.setURLStreamHandlerFactory(URLStreamHandlerFactory { protocol ->
        if (protocol != "https") null else object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection = FakeConnection(url)
        }
    })
    val jar = File(net.kdt.pojavlaunch.Tools.DIR_DATA, "authlib-injector/authlib-injector.jar")
    jar.parentFile.mkdirs()
    ZipOutputStream(jar.outputStream()).use { it.putNextEntry(ZipEntry("dummy")); it.write(1); it.closeEntry() }
    fun launch(): String {
        val args = AerixSkinServer.launchArguments(Context(), Account(AuthType.LOCAL, "Steve", null))
        check(args.size == 2) { "Local bridge must start even without a personal skin" }
        return args[0].substringAfter('=')
    }
    var base = launch()
    val metadata = JsonParser.parseString(URL("$base/").readText()).asJsonObject
    check(metadata.has("signaturePublickey"))
    val selfUuid = PojavAccountBridge.offlineUuid("Steve").toString().replace("-", "")
    val own = JsonParser.parseString(URL("$base/sessionserver/session/minecraft/profile/$selfUuid").readText()).asJsonObject
    check(own.get("name").asString == "Steve")
    val remote = JsonParser.parseString(URL("$base/sessionserver/session/minecraft/profile/$uuid").readText()).asJsonObject
    check(remote.get("name").asString == "Alex" && remote.get("id").asString == uuid)
    val prop = remote.getAsJsonArray("properties")[0].asJsonObject
    val texture = JsonParser.parseString(String(Base64.getDecoder().decode(prop.get("value").asString))).asJsonObject
    val skin = texture.getAsJsonObject("textures").getAsJsonObject("SKIN")
    check(skin.getAsJsonObject("metadata").get("model").asString == "slim")
    check(URL(skin.get("url").asString).readBytes().contentEquals(png))
    URL("$base/sessionserver/session/minecraft/profile/$uuid").readText()
    check(lookupCount == 1) { "Successful lookup was not cached" }
    AerixSkinServer.stop()
    lookupName = "WrongName"
    base = launch()
    val wrongName = URL("$base/sessionserver/session/minecraft/profile/$uuid").openConnection() as HttpURLConnection
    check(wrongName.responseCode == 204) { "Mismatched offline UUID was accepted" }
    AerixSkinServer.stop()
    lookupName = "Alex"
    redirects = true
    base = launch()
    val redirected = URL("$base/sessionserver/session/minecraft/profile/$uuid").openConnection() as HttpURLConnection
    check(redirected.responseCode == 204) { "Redirected image was accepted" }
    AerixSkinServer.stop()
    File(net.kdt.pojavlaunch.Tools.DIR_DATA).deleteRecursively()
    println("Offline local and shared profile bridge checks passed")
}
