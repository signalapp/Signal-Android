/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [LongClickCopySpan] is the click target for links in the story viewer, the long-message view, message details and starred messages —
 * every surface whose [UrlClickHandler] declines the click. [android.text.style.URLSpan]'s default launch omits
 * [Intent.FLAG_ACTIVITY_NEW_TASK], which would push the handling app onto our own task.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LongClickCopySpanTest {

  @Test
  fun `a clicked link is handed to the other app in its own task`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    LongClickCopySpan("https://signal.org/").onClick(View(activity))

    val intent = shadowOf(activity).nextStartedActivity
    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals("https://signal.org/", intent.dataString)
    assertNotEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
  }
}
