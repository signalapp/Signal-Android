/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.app.Application
import android.text.Spannable
import android.text.SpannableString
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.assertIs
import org.thoughtcrime.securesms.assertIsNull
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList.BodyRange.Style

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MessageStylerTest {

  private lateinit var text: Spannable

  @Before
  fun setUp() {
    text = SpannableString("This is a really long string for testing. Also thank you to all our beta testers!")
  }

  @Test
  fun nonOverlappingDifferentStyles() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 5)
    MessageStyler.toggleStyle(Style.ITALIC, text, 10, 15)

    val bodyRange = MessageStyler.getStyling(text)!!

    bodyRange.ranges.size assertIs 2

    bodyRange.ranges[0].apply {
      style assertIs Style.BOLD
      start assertIs 0
      length assertIs 5
    }

    bodyRange.ranges[1].apply {
      style assertIs Style.ITALIC
      start assertIs 10
      length assertIs 5
    }
  }

  @Test
  fun overlappingDifferentStyles() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 5)
    MessageStyler.toggleStyle(Style.ITALIC, text, 3, 10)

    val bodyRange = MessageStyler.getStyling(text)!!

    bodyRange.ranges.size assertIs 2

    bodyRange.ranges[0].apply {
      style assertIs Style.BOLD
      start assertIs 0
      length assertIs 5
    }

    bodyRange.ranges[1].apply {
      style assertIs Style.ITALIC
      start assertIs 3
      length assertIs 7
    }
  }

  @Test
  fun overlappingBeginning() {
    MessageStyler.toggleStyle(Style.BOLD, text, 3, 10)
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 5)

    val bodyRange = MessageStyler.getStyling(text)!!

    bodyRange.ranges.size assertIs 1

    bodyRange.ranges[0].apply {
      style assertIs Style.BOLD
      start assertIs 0
      length assertIs 10
    }
  }

  @Test
  fun overlappingEnd() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 5)
    MessageStyler.toggleStyle(Style.BOLD, text, 3, 10)

    val bodyRange = MessageStyler.getStyling(text)!!

    bodyRange.ranges.size assertIs 1

    bodyRange.ranges[0].apply {
      style assertIs Style.BOLD
      start assertIs 0
      length assertIs 10
    }
  }

  @Test
  fun overlappingContained() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 10)
    MessageStyler.toggleStyle(Style.BOLD, text, 4, 6)

    val bodyRange = MessageStyler.getStyling(text)!!

    bodyRange.ranges.size assertIs 2

    bodyRange.ranges[0].apply {
      style assertIs Style.BOLD
      start assertIs 0
      length assertIs 4
    }

    bodyRange.ranges[1].apply {
      style assertIs Style.BOLD
      start assertIs 6
      length assertIs 4
    }
  }

  @Test
  fun overlappingCovering() {
    MessageStyler.toggleStyle(Style.BOLD, text, 4, 6)
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 10)

    val bodyRange = MessageStyler.getStyling(text)!!

    bodyRange.ranges.size assertIs 1

    bodyRange.ranges[0].apply {
      style assertIs Style.BOLD
      start assertIs 0
      length assertIs 10
    }
  }

  @Test
  fun overlappingExact() {
    MessageStyler.toggleStyle(Style.BOLD, text, 4, 6)
    MessageStyler.toggleStyle(Style.BOLD, text, 4, 6)

    val bodyRange = MessageStyler.getStyling(text)

    bodyRange.assertIsNull()
  }

  @Test
  fun overlappingCoveringMultiple() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 3)
    MessageStyler.toggleStyle(Style.BOLD, text, 6, 8)
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 10)

    val bodyRange = MessageStyler.getStyling(text)!!

    bodyRange.ranges.size assertIs 1

    bodyRange.ranges[0].apply {
      style assertIs Style.BOLD
      start assertIs 0
      length assertIs 10
    }
  }

  @Test
  fun overlappingEndAndBeginning() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 3)
    MessageStyler.toggleStyle(Style.BOLD, text, 6, 8)
    MessageStyler.toggleStyle(Style.BOLD, text, 2, 7)

    val bodyRange = MessageStyler.getStyling(text)!!

    bodyRange.ranges.size assertIs 1

    bodyRange.ranges[0].apply {
      style assertIs Style.BOLD
      start assertIs 0
      length assertIs 8
    }
  }

  @Test
  fun clearFormatting() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 10)
    MessageStyler.clearStyling(text, 3, 7)

    val bodyRange = MessageStyler.getStyling(text)!!

    bodyRange.ranges.size assertIs 2

    bodyRange.ranges[0].apply {
      style assertIs Style.BOLD
      start assertIs 0
      length assertIs 3
    }

    bodyRange.ranges[1].apply {
      style assertIs Style.BOLD
      start assertIs 7
      length assertIs 3
    }
  }
}
