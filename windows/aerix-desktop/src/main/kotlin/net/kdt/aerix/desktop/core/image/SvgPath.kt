package net.kdt.aerix.desktop.core.image

import java.awt.geom.Arc2D
import java.awt.geom.Path2D

/**
 * A small SVG path-data parser for `android:pathData` strings.
 *
 * The launcher's icon set lives in `res/drawable/aerix_ic_*.xml` as Android vector drawables —
 * 24×24 viewports with `M/L/H/V/C/S/Q/T/A/Z` path commands, including relative forms and the
 * comma-or-space separated "compact" syntax that `aapt`'s optimiser emits (`1 0,0 1`,`-.3.2`).
 * Rendering them straight from the source keeps every icon identical to the Android build instead
 * of shipping a hand-redrawn set, and Java2D fills them with the same even-odd / non-zero rule the
 * drawable declares.
 *
 * Only what the drawable format uses is supported, which is exactly the full path grammar.
 */
class SvgPath private constructor(private val path: Path2D) {

    fun path(): Path2D = path

    companion object {
        fun parse(data: String, viewportW: Float = 24f, viewportH: Float = 24f, offsetX: Float = 0f, offsetY: Float = 0f, scale: Float = 1f, evenOdd: Boolean = false): Path2D {
            val parser = Tokenizer(data)
            val out = Path2D.Float()
            if (evenOdd) out.windingRule = Path2D.WIND_EVEN_ODD
            var cx = 0f; var cy = 0f      // current point
            var startX = 0f; var startY = 0f
            var lastCtrlX = 0f; var lastCtrlY = 0f
            var lastCmd = ' '
            var cmd = ' '
            fun mapX(x: Float): Float = offsetX + x * scale
            fun mapY(y: Float): Float = offsetY + y * scale

            while (parser.hasMore()) {
                val next = parser.peekCommand()
                if (next != null) {
                    cmd = parser.readCommand()
                } else if (cmd == 'Z' || cmd == 'z' || cmd == ' ') {
                    break
                }
                when (cmd) {
                    'M', 'm' -> {
                        var first = true
                        do {
                            var x = parser.number(); var y = parser.number()
                            if (cmd == 'm') { x += cx; y += cy }
                            if (first) { out.moveTo(mapX(x), mapY(y)); startX = x; startY = y; first = false }
                            else out.lineTo(mapX(x), mapY(y))
                            cx = x; cy = y
                        } while (parser.hasNumber())
                        lastCmd = cmd
                    }
                    'L', 'l' -> {
                        do {
                            var x = parser.number(); var y = parser.number()
                            if (cmd == 'l') { x += cx; y += cy }
                            out.lineTo(mapX(x), mapY(y)); cx = x; cy = y
                        } while (parser.hasNumber())
                        lastCmd = cmd
                    }
                    'H', 'h' -> {
                        do {
                            var x = parser.number()
                            if (cmd == 'h') x += cx
                            out.lineTo(mapX(x), mapY(cy)); cx = x
                        } while (parser.hasNumber())
                        lastCmd = cmd
                    }
                    'V', 'v' -> {
                        do {
                            var y = parser.number()
                            if (cmd == 'v') y += cy
                            out.lineTo(mapX(cx), mapY(y)); cy = y
                        } while (parser.hasNumber())
                        lastCmd = cmd
                    }
                    'C', 'c' -> {
                        do {
                            var x1 = parser.number(); var y1 = parser.number()
                            var x2 = parser.number(); var y2 = parser.number()
                            var x = parser.number(); var y = parser.number()
                            if (cmd == 'c') { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy }
                            out.curveTo(mapX(x1), mapY(y1), mapX(x2), mapY(y2), mapX(x), mapY(y))
                            lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y
                        } while (parser.hasNumber())
                        lastCmd = cmd
                    }
                    'S', 's' -> {
                        do {
                            var x2 = parser.number(); var y2 = parser.number()
                            var x = parser.number(); var y = parser.number()
                            if (cmd == 's') { x2 += cx; y2 += cy; x += cx; y += cy }
                            val (rx, ry) = if (lastCmd in "CcSs") {
                                (2 * cx - lastCtrlX) to (2 * cy - lastCtrlY)
                            } else cx to cy
                            out.curveTo(mapX(rx), mapY(ry), mapX(x2), mapY(y2), mapX(x), mapY(y))
                            lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y
                        } while (parser.hasNumber())
                        lastCmd = cmd
                    }
                    'Q', 'q' -> {
                        do {
                            var x1 = parser.number(); var y1 = parser.number()
                            var x = parser.number(); var y = parser.number()
                            if (cmd == 'q') { x1 += cx; y1 += cy; x += cx; y += cy }
                            out.quadTo(mapX(x1), mapY(y1), mapX(x), mapY(y))
                            lastCtrlX = x1; lastCtrlY = y1; cx = x; cy = y
                        } while (parser.hasNumber())
                        lastCmd = cmd
                    }
                    'T', 't' -> {
                        do {
                            var x = parser.number(); var y = parser.number()
                            if (cmd == 't') { x += cx; y += cy }
                            val (qx, qy) = if (lastCmd in "QqTt") (2 * cx - lastCtrlX) to (2 * cy - lastCtrlY) else cx to cy
                            out.quadTo(mapX(qx), mapY(qy), mapX(x), mapY(y))
                            lastCtrlX = qx; lastCtrlY = qy; cx = x; cy = y
                        } while (parser.hasNumber())
                        lastCmd = cmd
                    }
                    'A', 'a' -> {
                        do {
                            val rx = parser.number(); val ry = parser.number()
                            val rotation = parser.number()
                            val largeArc = parser.number() != 0f
                            val sweep = parser.number() != 0f
                            var x = parser.number(); var y = parser.number()
                            if (cmd == 'a') { x += cx; y += cy }
                            arcTo(out, cx, cy, rx, ry, rotation, largeArc, sweep, x, y, ::mapX, ::mapY)
                            cx = x; cy = y
                        } while (parser.hasNumber())
                        lastCmd = cmd
                    }
                    'Z', 'z' -> {
                        out.closePath(); cx = startX; cy = startY
                        lastCmd = cmd
                    }
                    else -> parser.skipUnknown()
                }
            }
            return out
        }

        /**
         * Endpoint-parameterised elliptical arc, converted to centre parameterisation, then
         * approximated with up to four cubic segments (the standard SVG implementation notes
         * appendix F.6.5 algorithm).
         */
        private fun arcTo(
            out: Path2D.Float, x1: Float, y1: Float, rxIn: Float, ryIn: Float, rotDeg: Float,
            largeArc: Boolean, sweep: Boolean, x2: Float, y2: Float,
            mapX: (Float) -> Float, mapY: (Float) -> Float
        ) {
            var rx = kotlin.math.abs(rxIn); var ry = kotlin.math.abs(ryIn)
            if (rx == 0f || ry == 0f || (x1 == x2 && y1 == y2)) {
                out.lineTo(mapX(x2), mapY(y2)); return
            }
            val phi = Math.toRadians(rotDeg.toDouble())
            val cosPhi = Math.cos(phi); val sinPhi = Math.sin(phi)
            val dx2 = (x1 - x2) / 2.0; val dy2 = (y1 - y2) / 2.0
            val x1p = cosPhi * dx2 + sinPhi * dy2
            val y1p = -sinPhi * dx2 + cosPhi * dy2
            var lambda = (x1p * x1p) / (rx * rx.toDouble()) + (y1p * y1p) / (ry * ry.toDouble())
            if (lambda > 1.0) { val s = Math.sqrt(lambda); rx = (rx * s).toFloat(); ry = (ry * s).toFloat(); lambda = 1.0 }
            val sign = if (largeArc != sweep) 1.0 else -1.0
            val num = rx * rx.toDouble() * ry * ry - rx * rx.toDouble() * y1p * y1p - ry * ry.toDouble() * x1p * x1p
            val den = rx * rx.toDouble() * y1p * y1p + ry * ry.toDouble() * x1p * x1p
            val coef = if (den == 0.0) 0.0 else sign * Math.sqrt((num / den).coerceAtLeast(0.0))
            val cxp = coef * (rx * y1p / ry)
            val cyp = coef * (-ry * x1p / rx)
            val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0
            val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0
            fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
                val dot = ux * vx + uy * vy
                val len = Math.sqrt(ux * ux + uy * uy) * Math.sqrt(vx * vx + vy * vy)
                var a = Math.acos((dot / len).coerceIn(-1.0, 1.0))
                if (ux * vy - uy * vx < 0) a = -a
                return a
            }
            val ux = (x1p - cxp) / rx; val uy = (y1p - cyp) / ry
            val vx = (-x1p - cxp) / rx; val vy = (-y1p - cyp) / ry
            val theta1 = angle(1.0, 0.0, ux, uy)
            var dTheta = angle(ux, uy, vx, vy)
            if (!sweep && dTheta > 0) dTheta -= 2 * Math.PI
            if (sweep && dTheta < 0) dTheta += 2 * Math.PI

            val segments = Math.ceil(Math.abs(dTheta / (Math.PI / 2))).toInt().coerceIn(1, 8)
            val delta = dTheta / segments
            val t = 4.0 / 3.0 * Math.tan(delta / 4.0)
            var th = theta1
            for (i in 0 until segments) {
                val th2 = th + delta
                fun pt(a: Double, out2: DoubleArray) {
                    val cosA = Math.cos(a); val sinA = Math.sin(a)
                    out2[0] = cx + rx * cosA * cosPhi - ry * sinA * sinPhi
                    out2[1] = cy + rx * cosA * sinPhi + ry * sinA * cosPhi
                }
                fun deriv(a: Double, out2: DoubleArray) {
                    val cosA = Math.cos(a); val sinA = Math.sin(a)
                    out2[0] = -rx * sinA * cosPhi - ry * cosA * sinPhi
                    out2[1] = -rx * sinA * sinPhi + ry * cosA * cosPhi
                }
                val p = DoubleArray(2); pt(th, p)
                val p2 = DoubleArray(2); pt(th2, p2)
                val d1 = DoubleArray(2); deriv(th, d1)
                val d2 = DoubleArray(2); deriv(th2, d2)
                out.curveTo(
                    mapX((p[0] + t * d1[0]).toFloat()), mapY((p[1] + t * d1[1]).toFloat()),
                    mapX((p2[0] - t * d2[0]).toFloat()), mapY((p2[1] - t * d2[1]).toFloat()),
                    mapX(p2[0].toFloat()), mapY(p2[1].toFloat())
                )
                th = th2
            }
        }
    }

    /** Number/command scanner following the SVG "path data" grammar, minus the lenient cases nobody emits. */
    private class Tokenizer(val s: String) {
        var i = 0

        fun hasMore(): Boolean { skipWs(); return i < s.length }

        fun peekCommand(): Char? {
            skipWs()
            if (i >= s.length) return null
            val c = s[i]
            return if (c.isLetter() && c !in "eE") c else null
        }

        fun readCommand(): Char {
            skipWs()
            return s[i++]
        }

        fun hasNumber(): Boolean {
            skipWs()
            if (i >= s.length) return false
            val c = s[i]
            return c.isDigit() || c == '-' || c == '+' || c == '.'
        }

        fun number(): Float {
            skipWs()
            val start = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            var digits = false
            while (i < s.length && s[i].isDigit()) { i++; digits = true }
            if (i < s.length && s[i] == '.') {
                i++
                while (i < s.length && s[i].isDigit()) { i++; digits = true }
            }
            if (digits && i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                val save = i
                i++
                if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
                var expDigits = false
                while (i < s.length && s[i].isDigit()) { i++; expDigits = true }
                if (!expDigits) i = save
            }
            if (i == start) throw IllegalArgumentException("expected a number at $start in path data")
            return s.substring(start, i).toFloat()
        }

        fun skipUnknown() { i++ }

        private fun skipWs() {
            while (i < s.length) {
                val c = s[i]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ',') i++ else break
            }
        }
    }
}
