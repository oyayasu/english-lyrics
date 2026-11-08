package com.oyayasu.englishlyrics

import android.content.Context

object Prefs {
    private fun prefs(context: Context) = context.getSharedPreferences("english_lyrics_settings", Context.MODE_PRIVATE)

    fun isDark(context: Context): Boolean = prefs(context).getBoolean("dark", false)
    fun setDark(context: Context, value: Boolean) = prefs(context).edit().putBoolean("dark", value).apply()

    fun opacity(context: Context): Float = prefs(context).getFloat("opacity", 0.94f).coerceIn(0.78f, 1f)
    fun setOpacity(context: Context, value: Float) = prefs(context).edit().putFloat("opacity", value.coerceIn(0.78f, 1f)).apply()

    fun textSize(context: Context): Float = prefs(context).getFloat("font_size", 20f).coerceIn(14f, 32f)
    fun setTextSize(context: Context, value: Float) = prefs(context).edit().putFloat("font_size", value.coerceIn(14f, 32f)).apply()

    fun bubbleX(context: Context): Int = prefs(context).getInt("bubble_x", 16)
    fun bubbleY(context: Context): Int = prefs(context).getInt("bubble_y", 220)
    fun setBubblePosition(context: Context, x: Int, y: Int) =
        prefs(context).edit().putInt("bubble_x", x).putInt("bubble_y", y).apply()
}
