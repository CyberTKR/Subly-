package com.cybertkr.suboverlay

import com.cybertkr.suboverlay.core.SubtitleCue

object SubtitleRepository {
    @Volatile var cues: List<SubtitleCue> = emptyList()
    @Volatile var title: String? = null
    @Volatile var savedOffsetMs: Long = 0L
}
