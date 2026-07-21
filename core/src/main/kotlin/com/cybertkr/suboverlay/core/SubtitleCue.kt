package com.cybertkr.suboverlay.core

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

data class ParseResult(val cues: List<SubtitleCue>, val skipped: Int)
