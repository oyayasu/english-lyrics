package com.oyayasu.englishlyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsParserTest {
    @Test fun stripsTimestampsButKeepsLyrics() {
        val input = "[00:05.42]Hello there\n[00:08.00]And goodbye"
        assertEquals("Hello there\nAnd goodbye", cleanTimestampedLyrics(input))
    }

    @Test fun retainsPlainTextAndUnicode() {
        assertEquals("東京\nHello", cleanTimestampedLyrics("東京\nHello"))
    }
}
