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
 * Supports legacy Yggdrasil (1.7.10 - 1.20.1) as well as authlib 5.x/6.x/7.x (1.20.2 - 1.21+ / 26.x)
 * including minecraftservices profile lookups, prefetched Yggdrasil metadata, 64x64 texture
 * normalization for SkinTextureDownloader, and non-nil offline player UUIDs.
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
import java.net.URLDecoder
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
    internal const val MOJANG_SESSIONS = "https://sessionserver.mojang.com"
    internal const val MOJANG_AUTH = "https://authserver.mojang.com"
    internal const val MOJANG_API = "https://api.mojang.com"
    internal const val MOJANG_SERVICES = "https://api.minecraftservices.com"

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

    @Volatile
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var workers: ExecutorService? = null
    @Volatile
    private var port: Int = 0
    private val characters = ConcurrentHashMap<String, Character>()
    private val textures = ConcurrentHashMap<String, ByteArray>()
    @Volatile
    private var primaryCharacter: Character? = null
    /** True for accounts whose own session service still has to work (Microsoft). */
    @Volatile
    private var passThrough = false

    private val keyPair: KeyPair? by lazy {
        runCatching { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair() }
            .onFailure { Log.w(TAG, "No RSA key pair: " + it.message) }
            .getOrNull()
    }

    /**
     * Arguments that make the game read its skin from here, or an empty list when there is nothing
     * to serve (no skin equipped, no authlib-injector jar, or an account that brings its own
     * authentication server).
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
        primaryCharacter = null
        passThrough = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { workers?.shutdownNow() }
        workers = null
        port = 0
    }

    private fun prepare(context: Context, account: Account): List<String> {
        val authType = account.authType ?: AuthType.LOCAL
        if (authType.injectorUrl != null) return emptyList()

        val isSlim = PojavAccountBridge.isSlimModelFor(context, account)
        val isClientSession = runCatching {
            net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance()?.isAerixClient == true
        }.getOrDefault(false)

        val skinFile = PojavAccountBridge.customSkinFile(account)
        val capeFile = PojavAccountBridge.customCapeFile(account)
        val rawSkinBytes = if (skinFile.isFile && skinFile.length() > 0) skinFile.readBytes() else null
        val normalizedSkin = rawSkinBytes?.let { PojavAccountBridge.normalizeSkinBytesForGame(it) }
        val skinBytes = if (isClientSession) {
            net.kdt.pojavlaunch.aerix.client.AerixClientCosmetics.applyHatAccessoryToSkin(
                context,
                normalizedSkin,
                isSlim
            )
        } else {
            normalizedSkin
        }
        val capeBytes = if (isClientSession) {
            net.kdt.pojavlaunch.aerix.client.AerixClientCosmetics.resolveInGameCapeBytes(context)
                ?: (if (capeFile.isFile && capeFile.length() > 0) capeFile.readBytes() else null)
        } else {
            if (capeFile.isFile && capeFile.length() > 0) capeFile.readBytes() else null
        }
        if (skinBytes == null && capeBytes == null) return emptyList()

        val jar = File(Tools.DIR_DATA, "$AUTHLIB_FOLDER/$AUTHLIB_JAR")
        if (!jar.isFile) {
            Logger.appendToLog("Skin server: $jar is missing, the equipped skin cannot reach the game")
            return emptyList()
        }
        val readableJar = runCatching { java.util.zip.ZipFile(jar).use { } }.isSuccess
        if (!readableJar) {
            Logger.appendToLog("Skin server: $jar is not a readable jar; launching without it")
            return emptyList()
        }

        val playerName = account.username?.takeIf { it.isNotBlank() } ?: "Steve"
        val effectiveUuid = PojavAccountBridge.effectiveProfileId(account).replace("-", "").lowercase()
        val offlineUuid = PojavAccountBridge.offlineUuid(playerName).toString().replace("-", "").lowercase()
        val rawAccountUuid = account.profileId.orEmpty().replace("-", "").lowercase()

        val character = Character(
            uuid = effectiveUuid.ifBlank { offlineUuid },
            name = playerName,
            skinHash = skinBytes?.let { remember(it) },
            skinBytes = skinBytes,
            capeHash = capeBytes?.let { remember(it) },
            capeBytes = capeBytes,
            slim = isSlim
        )
        primaryCharacter = character
        characters[character.uuid.lowercase()] = character
        characters[offlineUuid] = character
        if (rawAccountUuid.length == 32) {
            characters[rawAccountUuid] = character
        }
        passThrough = authType == AuthType.MICROSOFT
        if (!passThrough) {
            characters[ZERO_UUID] = character
        }
        characters[character.name.lowercase()] = character

        val socket = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        port = socket.localPort
        val pool = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "AerixSkinServer").apply { isDaemon = true }
        }
        workers = pool
        pool.execute { acceptLoop(socket) }

        val metadataJson = metadata()
        val prefetched = Base64.encodeToString(metadataJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        Logger.appendToLog(
            "Skin server on 127.0.0.1:$port for ${character.name} (${character.uuid.take(8)}…): " +
                "skin=${character.skinHash != null} (${skinBytes?.size ?: 0}B) " +
                "cape=${character.capeHash != null} slim=${character.slim} " +
                "account=${authType.name} passThrough=$passThrough"
        )
        return listOf(
            "-javaagent:${jar.absolutePath}=http://127.0.0.1:$port",
            "-Dauthlibinjector.yggdrasil.prefetched=$prefetched"
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
        val headers: Map<String, String>,
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
            val method = parts[0].uppercase()
            val target = parts[1]
            val path = decodeUrlComponent(target.substringBefore('?'))
            val query = target.substringAfter('?', "")
                .split('&')
                .filter { it.contains('=') }
                .associate {
                    decodeUrlComponent(it.substringBefore('=')) to decodeUrlComponent(it.substringAfter('='))
                }
            val body = if (method == "POST" || method == "PUT") {
                val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 1_048_576) ?: 0
                val buffer = ByteArray(length)
                var read = 0
                while (read < length) {
                    val step = input.read(buffer, read, length - read)
                    if (step < 0) break
                    read += step
                }
                String(buffer, 0, read, Charsets.UTF_8)
            } else ""
            route(Request(method, path, query, headers, body), client.getOutputStream())
        } catch (error: Throwable) {
            Log.w(TAG, "Request failed: ${error.javaClass.simpleName}: ${error.message}")
        } finally {
            runCatching { client.close() }
        }
    }

    private fun decodeUrlComponent(raw: String): String =
        runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)

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
        val sendBody = request.method != "HEAD"
        when {
            path == "/" || path.isEmpty() ->
                respond(output, 200, "application/json; charset=utf-8", metadata().toByteArray(), sendBody)

            path == "/api/profiles/minecraft" ||
                path == "/minecraftservices/minecraft/profile/lookup/bulk/byname" ->
                respond(output, 200, "application/json; charset=utf-8", profiles(request.body).toByteArray(), sendBody)

            path.startsWith("/api/users/profiles/minecraft/") ||
                path.startsWith("/minecraftservices/minecraft/profile/lookup/name/") -> {
                val lookupName = path.substringAfterLast('/').lowercase()
                val character = characters[lookupName]
                if (character != null) {
                    val json = JsonObject().apply {
                        addProperty("id", character.uuid)
                        addProperty("name", character.name)
                    }.toString()
                    respond(output, 200, "application/json; charset=utf-8", json.toByteArray(), sendBody)
                } else {
                    forgeOrProxy(request, output)
                }
            }

            path.startsWith("/minecraftservices/minecraft/profile/lookup/") -> {
                val lookupUuid = path.substringAfterLast('/').lowercase().replace("-", "")
                val character = characters[lookupUuid]
                if (character != null) {
                    val effectiveId = lookupUuid.takeIf { it.length == 32 } ?: character.uuid
                    val json = JsonObject().apply {
                        addProperty("id", effectiveId)
                        addProperty("name", character.name)
                    }.toString()
                    respond(output, 200, "application/json; charset=utf-8", json.toByteArray(), sendBody)
                } else {
                    forgeOrProxy(request, output)
                }
            }

            path == "/minecraftservices/minecraft/profile" -> {
                val character = primaryCharacter
                if (character != null && !passThrough) {
                    respond(
                        output,
                        200,
                        "application/json; charset=utf-8",
                        servicesProfile(character).toByteArray(),
                        sendBody
                    )
                } else {
                    forgeOrProxy(request, output)
                }
            }

            path == "/authserver/validate" -> {
                if (!passThrough) respond(output, 204, null, ByteArray(0), sendBody)
                else forgeOrProxy(request, output)
            }

            path == "/sessionserver/session/minecraft/join" -> {
                if (!passThrough) respond(output, 204, null, ByteArray(0), sendBody)
                else forgeOrProxy(request, output)
            }

            path == "/sessionserver/session/minecraft/hasJoined" -> {
                val username = request.query["username"].orEmpty().lowercase()
                val character = characters[username]
                if (!passThrough && character != null) {
                    respond(output, 200, "application/json; charset=utf-8", complete(character).toByteArray(), sendBody)
                } else {
                    forgeOrProxy(request, output)
                }
            }

            path.startsWith("/sessionserver/session/minecraft/profile/") -> {
                val uuid = path.substringAfterLast('/').lowercase().replace("-", "")
                val character = characters[uuid]
                Logger.appendToLog("Skin server: profile request ${uuid.take(8)}… served=${character != null}")
                if (character != null) {
                    val profile = complete(character, requestedUuid = uuid)
                    respond(output, 200, "application/json; charset=utf-8", profile.toByteArray(), sendBody)
                } else {
                    forgeOrProxy(request, output)
                }
            }

            path.startsWith("/textures/") -> {
                val hash = path.substringAfter("/textures/").substringBefore('.')
                val bytes = textures[hash]
                Logger.appendToLog("Skin server: texture request served=${bytes != null} (${bytes?.size ?: 0} bytes)")
                if (bytes != null) respond(output, 200, "image/png", bytes, sendBody)
                else respond(output, 404, "text/plain; charset=utf-8", "not found".toByteArray(), sendBody)
            }

            path.startsWith("/skins/MinecraftSkins/") -> {
                val name = path.substringAfterLast('/').removeSuffix(".png").lowercase()
                val bytes = characters[name]?.skinBytes
                if (bytes != null) respond(output, 200, "image/png", bytes, sendBody)
                else respond(output, 404, "text/plain; charset=utf-8", "not found".toByteArray(), sendBody)
            }

            else -> forgeOrProxy(request, output)
        }
    }

    /** Resolves `(upstreamHost, strippedPath)` for authlib-injector redirected paths. */
    internal fun resolveUpstreamTarget(path: String): Pair<String, String> = when {
        path.startsWith("/minecraftservices/") -> MOJANG_SERVICES to path.removePrefix("/minecraftservices")
        path == "/minecraftservices" -> MOJANG_SERVICES to "/"
        path.startsWith("/sessionserver/") -> MOJANG_SESSIONS to path.removePrefix("/sessionserver")
        path == "/sessionserver" -> MOJANG_SESSIONS to "/"
        path.startsWith("/authserver/") -> MOJANG_AUTH to path.removePrefix("/authserver")
        path == "/authserver" -> MOJANG_AUTH to "/"
        path.startsWith("/api/") -> MOJANG_API to path.removePrefix("/api")
        path == "/api" -> MOJANG_API to "/"
        else -> MOJANG_SESSIONS to path
    }

    /** Answers from Mojang when this launcher is not the one that knows the answer. */
    private fun forgeOrProxy(request: Request, output: OutputStream) {
        val sendBody = request.method != "HEAD"
        if (!passThrough) {
            // Return 204 No Content on unknown session/profile lookups so authlib treats them cleanly as absent.
            val status = if (request.method == "POST" || request.path.contains("/profile/")) 204 else 404
            respond(output, status, null, ByteArray(0), sendBody)
            return
        }
        val (host, upstreamPath) = resolveUpstreamTarget(request.path)
        val query = if (request.query.isEmpty()) ""
        else request.query.entries.joinToString("&", prefix = "?") { "${it.key}=${it.value}" }
        try {
            val connection = (URL(host + upstreamPath + query).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                request.headers["content-type"]?.let { setRequestProperty("Content-Type", it) }
                    ?: setRequestProperty("Content-Type", "application/json")
                request.headers["authorization"]?.let { setRequestProperty("Authorization", it) }
            }
            if (request.method == "POST" || request.method == "PUT") {
                connection.doOutput = true
                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            val type = connection.contentType ?: if (bytes.isNotEmpty()) "application/json" else null
            connection.disconnect()
            respond(output, code, type, bytes, sendBody)
        } catch (error: Throwable) {
            Log.w(TAG, "Pass-through to $host$upstreamPath failed: ${error.message}")
            respond(output, 503, "application/json", "{\"error\":\"upstream unreachable\"}".toByteArray(), sendBody)
        }
    }

    private fun respond(
        output: OutputStream,
        code: Int,
        type: String?,
        body: ByteArray,
        sendBody: Boolean = true
    ) {
        val header = StringBuilder("HTTP/1.1 $code ${reason(code)}\r\n")
        if (type != null) header.append("Content-Type: ").append(type).append("\r\n")
        header.append("Content-Length: ").append(if (code == 204) 0 else body.size).append("\r\n")
        header.append("Cache-Control: no-store\r\n")
        header.append("Connection: close\r\n\r\n")
        output.use {
            it.write(header.toString().toByteArray(Charsets.UTF_8))
            if (sendBody && code != 204 && body.isNotEmpty()) it.write(body)
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
        val domains = JsonArray().apply {
            add("127.0.0.1")
            add("localhost")
            add(".minecraft.net")
            add(".mojang.com")
        }
        root.add("skinDomains", domains)
        val meta = JsonObject().apply {
            addProperty("serverName", "Aerix Launcher (local)")
            addProperty("implementationName", "Aerix Launcher")
            addProperty("implementationVersion", "1.6")
            addProperty("feature.no_mojang_namespace", !passThrough)
            addProperty("feature.enable_profile_key", true)
            addProperty("feature.legacy_skin_api", true)
            addProperty("feature.enable_mojang_anti_features", false)
        }
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

    private fun servicesProfile(character: Character): String {
        val skins = JsonArray()
        character.skinHash?.let { hash ->
            skins.add(JsonObject().apply {
                addProperty("id", hash.take(32))
                addProperty("state", "ACTIVE")
                addProperty("url", "http://127.0.0.1:$port/textures/$hash")
                addProperty("variant", if (character.slim) "SLIM" else "CLASSIC")
            })
        }
        val capes = JsonArray()
        character.capeHash?.let { hash ->
            capes.add(JsonObject().apply {
                addProperty("id", hash.take(32))
                addProperty("state", "ACTIVE")
                addProperty("url", "http://127.0.0.1:$port/textures/$hash")
                addProperty("alias", "Aerix")
            })
        }
        return JsonObject().apply {
            addProperty("id", character.uuid)
            addProperty("name", character.name)
            add("skins", skins)
            add("capes", capes)
        }.toString()
    }

    /** The session-service answer the game reads the player's skin from. */
    private fun complete(character: Character, requestedUuid: String? = null): String {
        val effectiveId = requestedUuid?.takeIf { it.length == 32 } ?: character.uuid
        val texturesObject = JsonObject().apply {
            addProperty("timestamp", System.currentTimeMillis())
            addProperty("profileId", effectiveId)
            addProperty("profileName", character.name)
        }
        val texturesNode = JsonObject()
        character.skinHash?.let { hash ->
            texturesNode.add("SKIN", JsonObject().apply {
                addProperty("url", "http://127.0.0.1:$port/textures/$hash")
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

        val encoded = Base64.encodeToString(texturesObject.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val signature = sign(encoded)
        val property = JsonObject().apply {
            addProperty("name", "textures")
            addProperty("value", encoded)
            if (signature.isNotEmpty()) {
                addProperty("signature", signature)
            }
        }
        val properties = JsonArray().apply { add(property) }
        return JsonObject().apply {
            addProperty("id", effectiveId)
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
