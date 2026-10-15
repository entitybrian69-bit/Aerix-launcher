/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/game/elements/log_parser/Highlighter.kt
 * Colours follow mirai's AerixSurface syntax tokens, which the PojavLauncher AerixSurface carries
 * (syntaxMuted, syntaxString, syntaxAccent).
 * Zalith Launcher 2 Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.log

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import net.kdt.pojavlaunch.aerix.theme.AerixSurface

class LogHighlighter(
    val defaultColor: Color = Color.White,
    val timeColor: Color = AerixSurface.syntaxMuted,
    val stringColor: Color = AerixSurface.syntaxString,
    val numberColor: Color = AerixSurface.syntaxAccent,
    val packageColor: Color = AerixSurface.syntaxAccent,
    val linkColor: Color = AerixSurface.syntaxAccent
) {
    fun highlight(logText: String): AnnotatedString {
        return runCatching {
            highlightInternal(logText)
        }.getOrElse {
            // Any parser failure falls back to the default colour, exactly like the Mirai highlighter.
            AnnotatedString(
                text = logText,
                spanStyles = listOf(
                    AnnotatedString.Range(
                        SpanStyle(color = defaultColor),
                        0,
                        logText.length
                    )
                )
            )
        }
    }

    private fun highlightInternal(logText: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        val n = logText.length
        var inStackTrace = false

        while (i < n) {
            // Java stack traces
            if (LogParseCore.isLineStart(logText, i) && LogParseCore.isStackTraceStart(logText, i)) {
                inStackTrace = true
            }

            if (inStackTrace) {
                val lineEnd = logText.indexOf('\n', i).let { if (it == -1) n else it + 1 }
                withStyle(SpanStyle(color = stringColor)) {
                    append(logText.substring(i, lineEnd))
                }
                if (lineEnd < n && !LogParseCore.isStackTraceLine(logText, lineEnd)) {
                    inStackTrace = false
                }
                i = lineEnd
                continue
            }

            // Quoted strings
            if (logText[i] == '"' || logText[i] == '\'') {
                fun styleString(): Boolean {
                    val quote = logText[i]
                    val end = LogParseCore.findClosingQuote(logText, i, quote)
                    if (end != -1) {
                        withStyle(SpanStyle(color = stringColor)) {
                            append(logText.substring(i, end + 1))
                        }
                        i = end + 1
                        return true
                    }
                    return false
                }

                if (logText[i] == '\'') {
                    // A single quote only starts a string when it follows a space (Mirai behaviour).
                    if (logText.getOrNull(i - 1) == ' ' && styleString()) continue
                } else if (styleString()) {
                    continue
                }
            }

            // Web links
            val linkMatch = LogParseCore.matchWebLink(logText, i)
            if (linkMatch != null) {
                withStyle(SpanStyle(color = linkColor)) {
                    append(linkMatch)
                }
                i += linkMatch.length
                continue
            }

            // Timestamps
            val timeMatch = LogParseCore.matchTime(logText, i)
            if (timeMatch != null) {
                withStyle(SpanStyle(color = timeColor)) {
                    append(timeMatch)
                }
                i += timeMatch.length
                continue
            }

            // Log levels
            if (LogParseCore.isLogLevel(logText, i)) {
                val level = LogParseCore.matchLogLevel(logText, i)
                if (level != null) {
                    val rule = LogParseCore.findLevelRule(level)
                    withStyle(
                        SpanStyle(
                            color = rule?.textColor ?: defaultColor,
                            background = rule?.backgroundColor ?: Color.Unspecified
                        )
                    ) {
                        append(level)
                    }
                    i += level.length
                    continue
                }
            }

            // Numbers
            if (logText[i].isDigit()) {
                val segment = LogParseCore.scanNumericSegment(logText, i)
                if (segment != null) {
                    if (LogParseCore.isIndependentNumber(logText, i, segment.length)) {
                        withStyle(SpanStyle(color = numberColor)) {
                            append(segment)
                        }
                    } else {
                        append(segment)
                    }
                    i += segment.length
                    continue
                }
            }

            // Package names
            val packageName = LogParseCore.scanPackageName(logText, i)
            if (packageName != null) {
                withStyle(SpanStyle(color = packageColor)) {
                    append(packageName)
                }
                i += packageName.length
                continue
            }

            append(logText[i])
            i++
        }
    }
}
