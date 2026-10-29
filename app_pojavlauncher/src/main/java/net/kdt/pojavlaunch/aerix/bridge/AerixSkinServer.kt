/*
 * Aerix Launcher UI layer for PojavLauncher.
 * In-game skin bridge: hands the skin chosen in the Skin Wardrobe to Minecraft itself.
 *
 * A launcher cannot paint a skin onto the player from the outside: the game asks the session
 * service of the account for the profile of the local player and reads the skin texture URL out
 * of the "textures" property it gets back. That is why a skin equipped in the wardrobe used to be
 * visible everywhere in the launcher and nowhere in the game.
 *
 * This runs a tiny Yggdrasil ("authlib-injector") endpoint on the loopback interface with the
 * account's equipped skin, cape and Wide/Slim model, and the game is started with the
 * authlib-injector jar this launcher already ships pointed at it. The game then reads its profile
 * - and its texture - from here, exactly as it would from an authentication server.
 * The same design as Zalith Launcher 2 (game/account/offline/OfflineYggdrasilServer.kt) and HMCL.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.bridge

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.kdt.pojavlaunch.Logger
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.authenticator.AuthType
import net.kdt.pojavlaunch.authenticator.accounts.Account
import java.io.BufferedInputStream
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Serves the equipped wardrobe skin to the game through a loopback Yggdrasil endpoint.
 *
 * Only one game runs at a time, so there is one server: [launchArguments] replaces the previous
 * one and [stop] retires it when the game is closed.
 */
object AerixSkinServer {
    private const val TAG = "AerixSkinServer"
    private const val AUTHLIB_FOLDER = "authlib-injector"
    private const val AUTHLIB_JAR = "authlib-injector.jar"
    private const val MOJANG_SESSIONS = "https://sessionserver.mojang.com"
    private const val MOJANG_AUTH = "https://authserver.mojang.com"
    private const val MOJANG_API = "https://api.mojang.com"

    /** One player: the profile the game is launched as, plus the textures equipped for it. */
    private class Character(
        val uuid: String,
        val name: String,
        val skinHash: String?,
        val skinBytes: ByteArray?,
        val capeHash: String?,
        val capeBytes: ByteArray?,
        val slim: Boolean
    )

    private var serverSocket: ServerSocket? = null
    private var workers: ExecutorService? = null
    private var port: Int = 0
    private val characters = ConcurrentHashMap<String, Character>()
    private val textures = ConcurrentHashMap<String, ByteArray>()
    /** True for accounts whose own session service still has to work (Microsoft, Ely.by). */
    private var passThrough = false
    /**
     * Generated on the first request, never on the launch thread: RSA keygen is slow. Null when the
     * device cannot generate one, in which case the profile is served unsigned - the game accepts
     * that for the player's own profile.
     */
    private val keyPair: KeyPair? by lazy {
        runCatching { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair() }
            .onFailure { Log.w(TAG, "No RSA key pair: " + it.message) }
            .getOrNull()
    }

    /**
     * Arguments that make the game read its skin from here, or an empty list when there is nothing
     * to serve (no skin equipped, no authlib-injector jar, or an account that brings its own
     * authentication server).
     *
     * @param context used to read the Wide/Slim choice, which is stored per account
     * @param account the account the game is about to be launched as
     */
    @JvmStatic
    fun launchArguments(context: Context, account: Account): List<String> {
        stop()
        return try {
            prepare(context, account)
        } catch (error: Throwable) {
            Logger.appendToLog("Skin server failed: ${error.javaClass.simpleName}: ${error.message}")
            Log.e(TAG, "Could not start the skin server", error)
            stop()
            emptyList()
        }
    }

    /** Stops the endpoint; called when the game activity goes away. */
    @JvmStatic
    fun stop() {
        characters.clear()
        textures.clear()
        passThrough = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { workers?.shutdownNow() }
        workers = null
        port = 0
    }

    private fun prepare(context: Context, account: Account): List<String> {
        val authType = account.authType ?: AuthType.LOCAL
        // An account that logs in through its own Yggdrasil (Ely.by) is already launched with an
        // authlib-injector agent; a second one for the same jar would conflict, and its own
        // service knows the account better than this endpoint can.
        if (authType.injectorUrl != null) return emptyList()

        val skinFile = PojavAccountBridge.customSkinFile(account)
        val capeFile = PojavAccountBridge.customCapeFile(account)
        val skinBytes = if (skinFile.isFile && skinFile.length() > 0) skinFile.readBytes() else null
        val capeBytes = if (capeFile.isFile && capeFile.length() > 0) capeFile.readBytes() else null
        if (skinBytes == null && capeBytes == null) return emptyList()

        val jar = File(Tools.DIR_DATA, "$AUTHLIB_FOLDER/$AUTHLIB_JAR")
        if (!jar.isFile) {
            Logger.appendToLog("Skin server: $jar is missing, the equipped skin cannot reach the game")
            return emptyList()
        }
        // A jar the JVM cannot read as an agent stops the launch before Minecraft starts, so it is
        // checked here, where doing nothing is still a working game.
        val readableJar = runCatching { java.util.zip.ZipFile(jar).use { } }.isSuccess
        if (!readableJar) {
            Logger.appendToLog("Skin server: $jar is not a readable jar; launching without it")
            return emptyList()
        }

        val uuid = account.profileId.orEmpty().replace("-", "").ifBlank { ZERO_UUID }
        val character = Character(
            uuid = uuid,
            name = account.username ?: "Steve",
            skinHash = skinBytes?.let { remember(it) },
            skinBytes = skinBytes,
            capeHash = capeBytes?.let { remember(it) },
            capeBytes = capeBytes,
            slim = PojavAccountBridge.isSlimModelFor(context, account)
        )
        characters[character.uuid.lowercase()] = character
        characters[character.name.lowercase()] = character
        // The Microsoft profile still has to be reachable for multiplayer: those requests are
        // passed on to Mojang, only the local player's own textures are answered here.
        passThrough = authType == AuthType.MICROSOFT

        val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        port = socket.localPort
        val pool = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "AerixSkinServer").apply { isDaemon = true }
        }
        workers = pool
        pool.execute { acceptLoop(socket) }

        Logger.appendToLog(
            "Skin server on 127.0.0.1:$port for ${character.name}: skin=${character.skinHash != null} " +
                "cape=${character.capeHash != null} slim=${character.slim} " +
                "account=${authType.name} passThrough=$passThrough"
        )
        return listOf(
            "-javaagent:${jar.absolutePath}=http://127.0.0.1:$port",
            // Client side only: the account was authenticated by the launcher, and the game must
            // not try to log in against this endpoint.
            "-Dauthlibinjector.side=client"
        )
    }

    private fun remember(bytes: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        textures[hash] = bytes
        return hash
    }

    // ---- HTTP ------------------------------------------------------------------------------

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: Throwable) {
                return
            }
            val pool = workers ?: run { runCatching { client.close() }; return }
            runCatching { pool.execute { serve(client) } }
                .onFailure { runCatching { client.close() } }
        }
    }

    private class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val body: String
    )

    private fun serve(client: Socket) {
        try {
            client.soTimeout = 15000
            val input = BufferedInputStream(client.getInputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] =
                    line.substring(colon + 1).trim()
            }
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")
                .split('&')
                .filter { it.contains('=') }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            val body = if (parts[0].equals("POST", true)) {
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val buffer = ByteArray(length)
                var read = 0
                while (read < length) {
                    val step = input.read(buffer, read, length - read)
                    if (step < 0) break
                    read += step
                }
                String(buffer, 0, read, Charsets.UTF_8)
            } else ""
            route(Request(parts[0].uppercase(), path, query, body), client.getOutputStream())
        } catch (error: Throwable) {
            Log.w(TAG, "Request failed: ${error.javaClass.simpleName}: ${error.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) return if (builder.isEmpty()) null else builder.toString()
            val char = value.toChar()
            if (char == '\n') return builder.toString().trimEnd('\r')
            builder.append(char)
            if (builder.length > 8192) return null
        }
    }

    private fun route(request: Request, output: OutputStream) {
        val path = request.path
        when {
            path == "/" || path.isEmpty() -> respond(output, 200, "application/json", metadata().toByteArray())
            path == "/api/profiles/minecraft" -> respond(
                output, 200, "application/json", profiles(request.body).toByteArray()
            )
            path == "/sessionserver/session/minecraft/join" -> respond(output, 204, null, ByteArray(0))
            path == "/sessionserver/session/minecraft/hasJoined" -> {
                val profile = characters[request.query["username"].orEmpty().lowercase()]?.let { complete(it) }
                if (profile != null) respond(output, 200, "application/json", profile.toByteArray())
                else forgeOrProxy(request, output, MOJANG_SESSIONS)
            }
            path.startsWith("/sessionserver/session/minecraft/profile/") -> {
                // The session service asks by UUID, and it writes it with dashes while the profile
                // the game was launched with has none: both forms have to find the player.
                val uuid = path.substringAfterLast('/').lowercase().replace("-", "")
                val profile = characters[uuid]?.let { complete(it) }
                Logger.appendToLog("Skin server: profile request ${uuid.take(8)}… served=${profile != null}")
                if (profile != null) respond(output, 200, "application/json", profile.toByteArray())
                else forgeOrProxy(request, output, MOJANG_SESSIONS)
            }
            path.startsWith("/textures/") -> {
                val bytes = textures[path.substringAfter("/textures/")]
                Logger.appendToLog("Skin server: texture request served=${bytes != null} (${bytes?.size ?: 0} bytes)")
                if (bytes != null) respond(output, 200, "image/png", bytes)
                else respond(output, 404, "text/plain", "not found".toByteArray())
            }
            else -> forgeOrProxy(request, output, null)
        }
    }

    /** Answers from Mojang when this launcher is not the one that knows the answer. */
    private fun forgeOrProxy(request: Request, output: OutputStream, base: String?) {
        if (!passThrough) {
            respond(output, if (request.method == "POST") 204 else 404, null, ByteArray(0))
            return
        }
        val host = base ?: when {
            request.path.startsWith("/authserver") -> MOJANG_AUTH
            request.path.startsWith("/api") -> MOJANG_API
            else -> MOJANG_SESSIONS
        }
        val query = if (request.query.isEmpty()) ""
        else request.query.entries.joinToString("&", prefix = "?") { "${it.key}=${it.value}" }
        try {
            val connection = (URL(host + request.path + query).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json")
            }
            if (request.method == "POST") {
                connection.doOutput = true
                connection.outputStream.use { it.write(request.body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            val type = connection.contentType ?: "application/json"
            connection.disconnect()
            respond(output, code, type, bytes)
        } catch (error: Throwable) {
            Log.w(TAG, "Pass-through to $host failed: ${error.message}")
            respond(output, 503, "application/json", "{\"error\":\"upstream unreachable\"}".toByteArray())
        }
    }

    private fun respond(output: OutputStream, code: Int, type: String?, body: ByteArray) {
        val header = StringBuilder("HTTP/1.1 $code ${reason(code)}\r\n")
        if (type != null) header.append("Content-Type: ").append(type).append("\r\n")
        header.append("Content-Length: ").append(body.size).append("\r\n")
        header.append("Cache-Control: no-store\r\n")
        header.append("Connection: close\r\n\r\n")
        output.use {
            it.write(header.toString().toByteArray())
            if (body.isNotEmpty()) it.write(body)
            it.flush()
        }
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        204 -> "No Content"
        404 -> "Not Found"
        503 -> "Service Unavailable"
        else -> "Error"
    }

    // ---- Yggdrasil documents ---------------------------------------------------------------

    private fun metadata(): String {
        val root = JsonObject()
        val domains = JsonArray()
        domains.add("127.0.0.1")
        domains.add("localhost")
        root.add("skinDomains", domains)
        val meta = JsonObject()
        meta.addProperty("serverName", "Aerix Launcher (local)")
        meta.addProperty("implementationName", "Aerix Launcher")
        meta.addProperty("implementationVersion", "1.0")
        root.add("meta", meta)
        val key = keyPair
        if (key != null) {
            val pem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.encodeToString(key.public.encoded, Base64.NO_WRAP) +
                "\n-----END PUBLIC KEY-----\n"
            root.addProperty("signaturePublickey", pem)
        }
        return root.toString()
    }

    private fun profiles(body: String): String {
        val names = Regex("\"([^\"]*)\"").findAll(body).map { it.groupValues[1] }.toList()
        val array = JsonArray()
        for (name in names.distinct()) {
            val character = characters[name.lowercase()] ?: continue
            array.add(JsonObject().apply {
                addProperty("id", character.uuid)
                addProperty("name", character.name)
            })
        }
        return array.toString()
    }

    /** The session-service answer the game reads the player's skin from. */
    private fun complete(character: Character): String {
        val texturesObject = JsonObject()
        texturesObject.addProperty("timestamp", System.currentTimeMillis())
        texturesObject.addProperty("profileId", character.uuid)
        texturesObject.addProperty("profileName", character.name)
        val texturesNode = JsonObject()
        character.skinHash?.let { hash ->
            texturesNode.add("SKIN", JsonObject().apply {
                addProperty("url", "http://127.0.0.1:$port/textures/$hash")
                // Only a slim skin carries a model; without it the game draws the wide arms.
                if (character.slim) {
                    add("metadata", JsonObject().apply { addProperty("model", "slim") })
                }
            })
        }
        character.capeHash?.let { hash ->
            texturesNode.add("CAPE", JsonObject().apply {
                addProperty("url", "http://127.0.0.1:$port/textures/$hash")
            })
        }
        texturesObject.add("textures", texturesNode)

        val encoded = Base64.encodeToString(texturesObject.toString().toByteArray(), Base64.NO_WRAP)
        val property = JsonObject()
        property.addProperty("name", "textures")
        property.addProperty("value", encoded)
        property.addProperty("signature", sign(encoded))
        val properties = JsonArray()
        properties.add(property)
        return JsonObject().apply {
            addProperty("id", character.uuid)
            addProperty("name", character.name)
            add("properties", properties)
        }.toString()
    }

    private fun sign(data: String): String {
        val key = keyPair ?: return ""
        return runCatching {
            val signature = Signature.getInstance("SHA1withRSA")
            signature.initSign(key.private)
            signature.update(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
        }.getOrDefault("")
    }

    private const val ZERO_UUID = "00000000000000000000000000000000"
}
