package org.thoughtcrime.securesms.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.session.libsession.utilities.DelimiterUtil

class DelimiterUtilTest {
   
    @Test
    fun testEscape() {
        assertEquals(DelimiterUtil.escape("MTV Music", ' '), "MTV\\ Music")
        assertEquals(DelimiterUtil.escape("MTV  Music", ' '), "MTV\\ \\ Music")

        assertEquals(DelimiterUtil.escape("MTV,Music", ','), "MTV\\,Music")
        assertEquals(DelimiterUtil.escape("MTV,,Music", ','), "MTV\\,\\,Music")

        assertEquals(DelimiterUtil.escape("MTV Music", '+'), "MTV Music")
    }

    @Test
    fun testSplit() {
        var parts = DelimiterUtil.split("MTV\\ Music", ' ')
        assertEquals(parts.size, 1)
        assertEquals(parts[0], "MTV\\ Music")

        parts = DelimiterUtil.split("MTV Music", ' ')
        assertEquals(parts.size, 2)
        assertEquals(parts[0], "MTV")
        assertEquals(parts[1], "Music")
    }

    @Test
    fun testEscapeSplit() {
        var input = "MTV Music"
        var intermediate = DelimiterUtil.escape(input, ' ')
        var parts = DelimiterUtil.split(intermediate, ' ')

        assertEquals(parts.size, 1)
        assertEquals(parts[0], "MTV\\ Music")
        assertEquals(DelimiterUtil.unescape(parts[0], ' '), "MTV Music")

        input = "MTV\\ Music"
        intermediate = DelimiterUtil.escape(input, ' ')
        parts = DelimiterUtil.split(intermediate, ' ')

        assertEquals(parts.size, 1)
        assertEquals(parts[0], "MTV\\\\ Music")
        assertEquals(DelimiterUtil.unescape(parts[0], ' '), "MTV\\ Music")
    }
}