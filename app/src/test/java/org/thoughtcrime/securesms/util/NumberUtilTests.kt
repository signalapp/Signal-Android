package org.thoughtcrime.securesms.util

import org.junit.Assert.assertEquals
import org.junit.Test


class NumberUtilTests {

    @Test
    fun `it should display numbers less than 1000 as they are`() {
        val formatString = NumberUtil.getFormattedNumber(900)
        assertEquals("900", formatString)
    }

    @Test
    fun `it should display exactly 1000 as 1k`() {
        val formatString = NumberUtil.getFormattedNumber(1000)
        assertEquals("1k", formatString)
    }

    @Test
    fun `it should display numbers less than 10_000 properly`() {
        val formatString = NumberUtil.getFormattedNumber(1300)
        assertEquals("1.3k", formatString)
        val multipleKFormatString = NumberUtil.getFormattedNumber(3100)
        assertEquals("3.1k", multipleKFormatString)
    }

    @Test
    fun `it should display zero properly`() {
        val formatString = NumberUtil.getFormattedNumber(0)
        assertEquals("0", formatString)
    }

    @Test
    fun `it shouldn't care about negative numbers`() {
        val formatString = NumberUtil.getFormattedNumber(-10)
        assertEquals("-10", formatString)
    }

    @Test
    fun `it shouldn't get about large negative numbers`() {
        val formatString = NumberUtil.getFormattedNumber(-1200)
        assertEquals("-1.2k", formatString)
    }

    @Test
    fun `it should display numbers above 10k properly`() {
        val formatString = NumberUtil.getFormattedNumber(12300)
        assertEquals("12.3k", formatString)
    }

    @Test
    fun `it should display numbers above 100k properly`() {
        val formatString = NumberUtil.getFormattedNumber(132560)
        assertEquals("132.5k", formatString)
    }

}