/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.app.Application
import android.text.Spannable
import android.text.SpannableString
import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import assertk.assertions.single
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList.BodyRange
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

    val bodyRange = MessageStyler.getStyling(text)

    assertThat(bodyRange)
      .isNotNull()
      .transform {
        assertThat(it.ranges).hasSize(2)
        assertThat(it.ranges[0]).all {
          prop(BodyRange::style).isEqualTo(Style.BOLD)
          prop(BodyRange::start).isEqualTo(0)
          prop(BodyRange::length).isEqualTo(5)
        }
        assertThat(it.ranges[1]).all {
          prop(BodyRange::style).isEqualTo(Style.ITALIC)
          prop(BodyRange::start).isEqualTo(10)
          prop(BodyRange::length).isEqualTo(5)
        }
      }
  }

  @Test
  fun overlappingDifferentStyles() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 5)
    MessageStyler.toggleStyle(Style.ITALIC, text, 3, 10)

    val bodyRange = MessageStyler.getStyling(text)

    assertThat(bodyRange)
      .isNotNull()
      .transform {
        assertThat(it.ranges).hasSize(2)
        assertThat(it.ranges[0]).all {
          prop(BodyRange::style).isEqualTo(Style.BOLD)
          prop(BodyRange::start).isEqualTo(0)
          prop(BodyRange::length).isEqualTo(5)
        }
        assertThat(it.ranges[1]).all {
          prop(BodyRange::style).isEqualTo(Style.ITALIC)
          prop(BodyRange::start).isEqualTo(3)
          prop(BodyRange::length).isEqualTo(7)
        }
      }
  }

  @Test
  fun overlappingBeginning() {
    MessageStyler.toggleStyle(Style.BOLD, text, 3, 10)
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 5)

    val bodyRange = MessageStyler.getStyling(text)

    assertThat(bodyRange)
      .isNotNull()
      .transform { it.ranges }
      .single()
      .all {
        prop(BodyRange::style).isEqualTo(Style.BOLD)
        prop(BodyRange::start).isEqualTo(0)
        prop(BodyRange::length).isEqualTo(10)
      }
  }

  @Test
  fun overlappingEnd() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 5)
    MessageStyler.toggleStyle(Style.BOLD, text, 3, 10)

    val bodyRange = MessageStyler.getStyling(text)

    assertThat(bodyRange)
      .isNotNull()
      .transform { it.ranges }
      .single()
      .all {
        prop(BodyRange::style).isEqualTo(Style.BOLD)
        prop(BodyRange::start).isEqualTo(0)
        prop(BodyRange::length).isEqualTo(10)
      }
  }

  @Test
  fun overlappingContained() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 10)
    MessageStyler.toggleStyle(Style.BOLD, text, 4, 6)

    val bodyRange = MessageStyler.getStyling(text)

    assertThat(bodyRange)
      .isNotNull()
      .transform {
        assertThat(it.ranges).hasSize(2)
        assertThat(it.ranges[0]).all {
          prop(BodyRange::style).isEqualTo(Style.BOLD)
          prop(BodyRange::start).isEqualTo(0)
          prop(BodyRange::length).isEqualTo(4)
        }
        assertThat(it.ranges[1]).all {
          prop(BodyRange::style).isEqualTo(Style.BOLD)
          prop(BodyRange::start).isEqualTo(6)
          prop(BodyRange::length).isEqualTo(4)
        }
      }
  }

  @Test
  fun overlappingCovering() {
    MessageStyler.toggleStyle(Style.BOLD, text, 4, 6)
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 10)

    val bodyRange = MessageStyler.getStyling(text)

    assertThat(bodyRange)
      .isNotNull()
      .transform { it.ranges }
      .single()
      .all {
        prop(BodyRange::style).isEqualTo(Style.BOLD)
        prop(BodyRange::start).isEqualTo(0)
        prop(BodyRange::length).isEqualTo(10)
      }
  }

  @Test
  fun overlappingExact() {
    MessageStyler.toggleStyle(Style.BOLD, text, 4, 6)
    MessageStyler.toggleStyle(Style.BOLD, text, 4, 6)

    val bodyRange = MessageStyler.getStyling(text)

    assertThat(bodyRange).isNull()
  }

  @Test
  fun overlappingCoveringMultiple() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 3)
    MessageStyler.toggleStyle(Style.BOLD, text, 6, 8)
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 10)

    val bodyRange = MessageStyler.getStyling(text)

    assertThat(bodyRange)
      .isNotNull()
      .transform { it.ranges }
      .single()
      .all {
        prop(BodyRange::style).isEqualTo(Style.BOLD)
        prop(BodyRange::start).isEqualTo(0)
        prop(BodyRange::length).isEqualTo(10)
      }
  }

  @Test
  fun overlappingEndAndBeginning() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 3)
    MessageStyler.toggleStyle(Style.BOLD, text, 6, 8)
    MessageStyler.toggleStyle(Style.BOLD, text, 2, 7)

    val bodyRange = MessageStyler.getStyling(text)

    assertThat(bodyRange)
      .isNotNull()
      .transform { it.ranges }
      .single()
      .all {
        prop(BodyRange::style).isEqualTo(Style.BOLD)
        prop(BodyRange::start).isEqualTo(0)
        prop(BodyRange::length).isEqualTo(8)
      }
  }

  @Test
  fun clearFormatting() {
    MessageStyler.toggleStyle(Style.BOLD, text, 0, 10)
    MessageStyler.clearStyling(text, 3, 7)

    val bodyRange = MessageStyler.getStyling(text)

    assertThat(bodyRange)
      .isNotNull()
      .transform {
        assertThat(it.ranges).hasSize(2)
        assertThat(it.ranges[0]).all {
          prop(BodyRange::style).isEqualTo(Style.BOLD)
          prop(BodyRange::start).isEqualTo(0)
          prop(BodyRange::length).isEqualTo(3)
        }
        assertThat(it.ranges[1]).all {
          prop(BodyRange::style).isEqualTo(Style.BOLD)
          prop(BodyRange::start).isEqualTo(7)
          prop(BodyRange::length).isEqualTo(3)
        }
      }
  }
}
