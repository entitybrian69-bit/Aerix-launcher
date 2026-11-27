package net.kdt.aerix.desktop.core.json

/**
 * Minimal JSON model + parser/writer.
 *
 * The desktop target deliberately carries no third-party runtime: Minecraft metadata, Modrinth,
 * CurseForge and the launcher's own settings are the only JSON this program reads, and all of
 * them fit comfortably in this file. It follows RFC 8259 (comments are tolerated, which some
 * loader metadata uses), keeps object order, and never throws on deep-but-sane input.
 */
sealed class JsonValue {
    open fun asString(): String? = (this as? JsonString)?.value
    open fun asLong(): Long? = when (this) {
        is JsonNumber -> value.toLong()
        is JsonString -> value.trim().toLongOrNull()
        else -> null
    }

    open fun asInt(): Int? = asLong()?.toInt()
    open fun asDouble(): Double? = when (this) {
        is JsonNumber -> value
        is JsonString -> value.trim().toDoubleOrNull()
        else -> null
    }

    open fun asBoolean(): Boolean? = when (this) {
        is JsonBool -> value
        is JsonString -> value.equals("true", true)
        else -> null
    }

    fun isNull(): Boolean = this is JsonNull

    operator fun get(key: String): JsonValue? = (this as? JsonObject)?.fields?.get(key)
    operator fun get(index: Int): JsonValue? = (this as? JsonArray)?.items?.getOrNull(index)

    /** Chainable safe navigation: obj.str("a", "b") to read a nested string. */
    fun str(vararg path: String): String? {
        var cur: JsonValue? = this
        for (p in path) cur = cur?.get(p) ?: return null
        return cur?.asString()
    }

    fun num(vararg path: String): Long? = navigate(path)?.asLong()
    fun obj(vararg path: String): JsonObject? = navigate(path) as? JsonObject
    fun arr(vararg path: String): JsonArray? = navigate(path) as? JsonArray
    fun bool(vararg path: String): Boolean? = navigate(path)?.asBoolean()
    fun has(key: String): Boolean = (this as? JsonObject)?.fields?.containsKey(key) == true

    private fun navigate(path: Array<out String>): JsonValue? {
        var cur: JsonValue? = this
        for (p in path) {
            cur = if (p.toIntOrNull() != null && cur is JsonArray) cur[p.toInt()] else cur?.get(p)
            if (cur == null) return null
        }
        return cur
    }

    abstract fun write(sb: StringBuilder, indent: Int, level: Int)
    override fun toString(): String = StringBuilder().also { write(it, 0, 0) }.toString()

    companion object {
        fun of(value: Any?): JsonValue = when (value) {
            null -> JsonNull
            is JsonValue -> value
            is String -> JsonString(value)
            is Boolean -> JsonBool(value)
            is Number -> JsonNumber(value.toDouble())
            is Map<*, *> -> JsonObject().also { o -> value.forEach { (k, v) -> o.put(k.toString(), of(v)) } }
            is Iterable<*> -> JsonArray().also { a -> value.forEach { a.add(of(it)) } }
            is Array<*> -> JsonArray().also { a -> value.forEach { a.add(of(it)) } }
            else -> JsonString(value.toString())
        }
    }
}

object JsonNull : JsonValue() {
    override fun write(sb: StringBuilder, indent: Int, level: Int) { sb.append("null") }
}

class JsonString(val value: String) : JsonValue() {
    override fun write(sb: StringBuilder, indent: Int, level: Int) { quote(sb, value) }
    override fun equals(other: Any?) = other is JsonString && other.value == value
    override fun hashCode() = value.hashCode()

    companion object {
        fun quote(sb: StringBuilder, value: String) {
            sb.append('"')
            var i = 0
            while (i < value.length) {
                val c = value[i]
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    else -> if (c < ' ') sb.append("\\u").append(String.format("%04x", c.code)) else sb.append(c)
                }
                i++
            }
            sb.append('"')
        }
    }
}

class JsonNumber(val value: Double) : JsonValue() {
    override fun write(sb: StringBuilder, indent: Int, level: Int) {
        if (value.isNaN() || value.isInfinite()) { sb.append("0"); return }
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e18) sb.append(value.toLong())
        else sb.append(value)
    }
    override fun equals(other: Any?) = other is JsonNumber && other.value == value
    override fun hashCode() = value.hashCode()
}

class JsonBool(val value: Boolean) : JsonValue() {
    override fun write(sb: StringBuilder, indent: Int, level: Int) { sb.append(if (value) "true" else "false") }
}

class JsonArray(val items: MutableList<JsonValue> = ArrayList()) : JsonValue(), Iterable<JsonValue> {
    fun add(v: JsonValue): JsonArray { items.add(v); return this }
    fun add(v: Any?): JsonArray = add(JsonValue.of(v))
    override fun iterator(): Iterator<JsonValue> = items.iterator()
    val size: Int get() = items.size
    fun strings(): List<String> = items.mapNotNull { it.asString() }
    fun objects(): List<JsonObject> = items.filterIsInstance<JsonObject>()

    override fun write(sb: StringBuilder, indent: Int, level: Int) {
        if (items.isEmpty()) { sb.append("[]"); return }
        val compact = indent == 0
        sb.append('[')
        items.forEachIndexed { i, v ->
            if (i > 0) sb.append(',')
            if (!compact) { sb.append('\n'); pad(sb, indent, level + 1) }
            v.write(sb, indent, level + 1)
        }
        if (!compact) { sb.append('\n'); pad(sb, indent, level) }
        sb.append(']')
    }
}

class JsonObject(val fields: LinkedHashMap<String, JsonValue> = LinkedHashMap()) : JsonValue() {
    fun put(key: String, value: JsonValue?): JsonObject {
        if (value == null || value is JsonNull) fields.remove(key) else fields[key] = value
        return this
    }
    fun put(key: String, value: Any?): JsonObject = put(key, JsonValue.of(value))
    fun putAll(other: JsonObject): JsonObject { other.fields.forEach { (k, v) -> fields[k] = v }; return this }
    fun remove(key: String): JsonValue? = fields.remove(key)
    fun keys(): Set<String> = fields.keys
    fun isEmpty(): Boolean = fields.isEmpty()
    fun copy(): JsonObject = Json.parse(toString()) as JsonObject

    override fun write(sb: StringBuilder, indent: Int, level: Int) {
        if (fields.isEmpty()) { sb.append("{}"); return }
        val compact = indent == 0
        sb.append('{')
        var first = true
        for ((k, v) in fields) {
            if (!first) sb.append(',')
            first = false
            if (!compact) { sb.append('\n'); pad(sb, indent, level + 1) }
            JsonString.quote(sb, k)
            sb.append(if (compact) ":" else ": ")
            v.write(sb, indent, level + 1)
        }
        if (!compact) { sb.append('\n'); pad(sb, indent, level) }
        sb.append('}')
    }
}

internal fun pad(sb: StringBuilder, indent: Int, level: Int) {
    for (i in 0 until indent * level) sb.append(' ')
}

class JsonParseException(message: String, val position: Int = -1) : RuntimeException(
    if (position >= 0) "$message (at offset $position)" else message
)

/** Recursive-descent RFC 8259 parser with comment tolerance. */
class Json private constructor(private val text: String) {
    private var pos = 0

    private fun fail(msg: String): Nothing = throw JsonParseException(msg, pos)

    private fun skipWs() {
        while (pos < text.length) {
            val c = text[pos]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { pos++ }
            else if (c == '/' && pos + 1 < text.length && text[pos + 1] == '/') {
                while (pos < text.length && text[pos] != '\n') pos++
            } else if (c == '/' && pos + 1 < text.length && text[pos + 1] == '*') {
                pos += 2
                while (pos + 1 < text.length && !(text[pos] == '*' && text[pos + 1] == '/')) pos++
                pos += 2
            } else break
        }
    }

    private fun parseValue(): JsonValue {
        skipWs()
        if (pos >= text.length) fail("unexpected end of input")
        return when (val c = text[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonString(parseString())
            't' -> { expect("true"); JsonBool(true) }
            'f' -> { expect("false"); JsonBool(false) }
            'n' -> { expect("null"); JsonNull }
            else -> if (c == '-' || c in '0'..'9') parseNumber() else fail("unexpected character '$c'")
        }
    }

    private fun expect(word: String) {
        if (!text.startsWith(word, pos)) fail("expected $word")
        pos += word.length
    }

    private fun parseObject(): JsonObject {
        val obj = JsonObject()
        pos++ // {
        skipWs()
        if (pos < text.length && text[pos] == '}') { pos++; return obj }
        while (true) {
            skipWs()
            if (pos >= text.length || text[pos] != '"') fail("expected object key")
            val key = parseString()
            skipWs()
            if (pos >= text.length || text[pos] != ':') fail("expected ':'")
            pos++
            obj.fields[key] = parseValue()
            skipWs()
            if (pos >= text.length) fail("unterminated object")
            when (text[pos]) {
                ',' -> pos++
                '}' -> { pos++; return obj }
                else -> fail("expected ',' or '}'")
            }
        }
    }

    private fun parseArray(): JsonArray {
        val arr = JsonArray()
        pos++ // [
        skipWs()
        if (pos < text.length && text[pos] == ']') { pos++; return arr }
        while (true) {
            arr.items.add(parseValue())
            skipWs()
            if (pos >= text.length) fail("unterminated array")
            when (text[pos]) {
                ',' -> pos++
                ']' -> { pos++; return arr }
                else -> fail("expected ',' or ']'")
            }
        }
    }

    private fun parseString(): String {
        if (text[pos] != '"') fail("expected string")
        pos++
        val sb = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("unterminated string")
            val c = text[pos++]
            when {
                c == '"' -> return sb.toString()
                c == '\\' -> {
                    if (pos >= text.length) fail("bad escape")
                    when (val e = text[pos++]) {
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                        'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail("bad unicode escape")
                            val hex = text.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toIntOrNull(16)?.toChar() ?: fail("bad unicode escape"))
                        }
                        else -> fail("bad escape '\\$e'")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun parseNumber(): JsonNumber {
        val start = pos
        if (pos < text.length && text[pos] == '-') pos++
        while (pos < text.length && text[pos].isDigit()) pos++
        if (pos < text.length && text[pos] == '.') {
            pos++
            while (pos < text.length && text[pos].isDigit()) pos++
        }
        if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
            pos++
            if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
            while (pos < text.length && text[pos].isDigit()) pos++
        }
        val raw = text.substring(start, pos)
        return JsonNumber(raw.toDoubleOrNull() ?: fail("bad number '$raw'"))
    }

    companion object {
        fun parse(text: String): JsonValue = Json(text).let {
            it.skipWs()
            val v = it.parseValue()
            it.skipWs()
            if (it.pos < text.length) throw JsonParseException("trailing content", it.pos)
            v
        }

        fun parseObject(text: String): JsonObject =
            parse(text) as? JsonObject ?: throw JsonParseException("expected a JSON object")

        fun parseArray(text: String): JsonArray =
            parse(text) as? JsonArray ?: throw JsonParseException("expected a JSON array")

        /** Pretty JSON, the form written to settings and manifests. */
        fun pretty(value: JsonValue): String = StringBuilder().also { value.write(it, 2, 0) }.toString()
    }
}

/** Builder DSL so constructing documents reads like the JSON they become. */
fun jsonObject(block: JsonObject.() -> Unit): JsonObject = JsonObject().apply(block)
fun jsonArray(vararg values: Any?): JsonArray = JsonArray().also { a -> values.forEach { a.add(it) } }
