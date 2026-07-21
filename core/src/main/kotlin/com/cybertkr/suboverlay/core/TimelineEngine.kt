package com.cybertkr.suboverlay.core

class TimelineEngine(cues: List<SubtitleCue>) {
    private val sorted = cues.sortedBy { it.startMs }

    fun cueAt(positionMs: Long): SubtitleCue? =
        sorted.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }
}
