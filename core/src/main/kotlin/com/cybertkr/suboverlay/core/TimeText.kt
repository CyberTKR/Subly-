package com.cybertkr.suboverlay.core

object TimeText {
    fun parseToMs(text: String): Long? {
        val parts = text.trim().split(":")
        if (parts.size !in 2..3) return null
        val n = parts.map { it.trim().toLongOrNull() ?: return null }
        return when (n.size) {
            2 -> (n[0] * 60 + n[1]) * 1000
            else -> ((n[0] * 3600) + n[1] * 60 + n[2]) * 1000
        }
    }
}
