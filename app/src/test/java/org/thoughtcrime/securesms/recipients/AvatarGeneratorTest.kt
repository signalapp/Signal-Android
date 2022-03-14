package org.thoughtcrime.securesms.recipients

import org.junit.Assert.assertEquals
import org.junit.Test
import org.thoughtcrime.securesms.util.AvatarPlaceholderGenerator

class AvatarGeneratorTest {

    @Test
    fun testCommonAvatarFormats() {
        val testNamesAndResults = mapOf(
            "H  " to "H",
            "Test Name" to "TN",
            "test name" to "TN",
            "howdy  partner" to "HP",
            "testname" to "TE", //
            "05aaapubkey" to "A", // pubkey values only return first non-05 character
            "Test" to "TE"
        )
        testNamesAndResults.forEach { (test, expected) ->
            val processed = AvatarPlaceholderGenerator.extractLabel(test)
            assertEquals(expected, processed)
        }
    }

}