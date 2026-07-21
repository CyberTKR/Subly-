package com.cybertkr.suboverlay.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView

class SubtitleView(context: Context) : TextView(context) {
    init {
        setTextColor(Color.WHITE)
        textSize = 20f
        gravity = Gravity.CENTER
        setPadding(24, 12, 24, 12)
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
        setBackgroundColor(Color.argb(140, 0, 0, 0))
        visibility = TextView.GONE
    }

    fun setLine(text: String?) {
        if (text.isNullOrEmpty()) { visibility = GONE; setText("") }
        else { setText(text); visibility = VISIBLE }
    }
}
