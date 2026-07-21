package com.cybertkr.suboverlay.core

object SrtParser {
    private val TIME = Regex(
        """(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})"""
    )

    private val TAG = Regex("""<[^>]*>|\{[^}]*\}""")

    fun parse(raw: String): ParseResult {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace("\r", "\n")
        val blocks = text.split(Regex("\n[ \t]*\n"))
        val cues = ArrayList<SubtitleCue>()
        var skipped = 0

        for (block in blocks) {
            val lines = block.split("\n").map { it.trim('﻿', ' ', '\t') }
                .dropWhile { it.isBlank() }
            if (lines.isEmpty() || lines.all { it.isBlank() }) continue

            val timeIdx = lines.indexOfFirst { TIME.containsMatchIn(it) }
            if (timeIdx < 0) { skipped++; continue }

            val m = TIME.find(lines[timeIdx]) ?: continue
            val start = toMs(m.groupValues, 1)
            val end = toMs(m.groupValues, 5)
            val body = TAG.replace(lines.drop(timeIdx + 1).joinToString("\n"), "")
                .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            if (body.isEmpty()) { skipped++; continue }
            cues.add(SubtitleCue(start, end, body))
        }
        return ParseResult(cues, skipped)
    }

    private fun toMs(g: List<String>, base: Int): Long {
        val h = g[base].toLong(); val mi = g[base + 1].toLong()
        val s = g[base + 2].toLong(); val ms = g[base + 3].toLong()
        return ((h * 60 + mi) * 60 + s) * 1000 + ms
    }
}
