package org.thoughtcrime.securesms.components

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class ComposeTextTest {

  @Test
  fun `couldBeTimeEntry returns true for valid time at start of text`() {
    assertThat(ComposeText.couldBeTimeEntry("1:08", 1)).isTrue()
    assertThat(ComposeText.couldBeTimeEntry("12:30", 2)).isTrue()
    assertThat(ComposeText.couldBeTimeEntry("9:45", 1)).isTrue()
    assertThat(ComposeText.couldBeTimeEntry("23:59", 2)).isTrue()
  }

  @Test
  fun `couldBeTimeEntry returns true for valid time in middle of text`() {
    assertThat(ComposeText.couldBeTimeEntry("Hello 1:08", 7)).isTrue()
    assertThat(ComposeText.couldBeTimeEntry("Meet at 12:30 today", 10)).isTrue()
    assertThat(ComposeText.couldBeTimeEntry("The time is 9:45 now", 14)).isTrue()
  }

  @Test
  fun `couldBeTimeEntry returns false for emoji queries at start`() {
    assertThat(ComposeText.couldBeTimeEntry(":smile", 1)).isFalse()
    assertThat(ComposeText.couldBeTimeEntry(":search", 1)).isFalse()
    assertThat(ComposeText.couldBeTimeEntry(":abc", 1)).isFalse()
  }

  @Test
  fun `couldBeTimeEntry returns false for emoji queries in middle of text`() {
    assertThat(ComposeText.couldBeTimeEntry("Hello :smile", 7)).isFalse()
    assertThat(ComposeText.couldBeTimeEntry("Testing :search now", 9)).isFalse()
  }

  @Test
  fun `couldBeTimeEntry returns false when text after colon is not numeric`() {
    assertThat(ComposeText.couldBeTimeEntry("test:abc", 5)).isFalse()
    assertThat(ComposeText.couldBeTimeEntry("hello:world", 6)).isFalse()
  }

  @Test
  fun `couldBeTimeEntry returns false for invalid time formats`() {
    assertThat(ComposeText.couldBeTimeEntry("1:2:3", 2)).isFalse()
    assertThat(ComposeText.couldBeTimeEntry("123:45", 4)).isFalse()
    assertThat(ComposeText.couldBeTimeEntry("12:345", 3)).isFalse()
  }

  @Test
  fun `couldBeTimeEntry returns false when startIndex is at boundary`() {
    assertThat(ComposeText.couldBeTimeEntry(":08", 0)).isFalse()
    assertThat(ComposeText.couldBeTimeEntry("1:", 1)).isFalse()
  }

  @Test
  fun `couldBeTimeEntry returns false when cursor is at end of text`() {
    assertThat(ComposeText.couldBeTimeEntry("1:0", 2)).isFalse()
  }

  @Test
  fun `couldBeTimeEntry returns true for time with leading zero`() {
    assertThat(ComposeText.couldBeTimeEntry("01:08", 2)).isTrue()
    assertThat(ComposeText.couldBeTimeEntry("10:05", 2)).isTrue()
  }

  @Test
  fun `couldBeTimeEntry handles time at end of text`() {
    assertThat(ComposeText.couldBeTimeEntry("Call me at 5:30", 13)).isTrue()
    assertThat(ComposeText.couldBeTimeEntry("Ends at 11:59", 10)).isTrue()
  }

  @Test
  fun `couldBeTimeEntry returns false for partial time entry still being typed`() {
    // When user has typed "1:" but hasn't typed the minutes yet
    assertThat(ComposeText.couldBeTimeEntry("1:", 1)).isFalse()
    assertThat(ComposeText.couldBeTimeEntry("12:", 2)).isFalse()
  }

  @Test
  fun `couldBeTimeEntry returns true for various valid time formats`() {
    // Single digit hour and minute
    assertThat(ComposeText.couldBeTimeEntry("1:1", 1)).isTrue()
    assertThat(ComposeText.couldBeTimeEntry("9:9", 1)).isTrue()

    // Double digit combinations
    assertThat(ComposeText.couldBeTimeEntry("11:11", 2)).isTrue()
    assertThat(ComposeText.couldBeTimeEntry("1:11", 1)).isTrue()
    assertThat(ComposeText.couldBeTimeEntry("11:1", 2)).isTrue()
  }
}
