/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.provider.Browser
import androidx.core.net.toUri
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LinkActionsTest {

  private val url = "https://signal.org/"

  @Test
  fun `link opened from an activity is given its own task`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    var errored = false
    LinkActions.openUrl(activity, url) { errored = true }

    val intent = shadowOf(activity).nextStartedActivity

    assertThat(errored).isFalse()
    assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
    assertThat(intent.dataString).isEqualTo(url)
    assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
  }

  @Test
  fun `link opened from a non-activity context is given its own task`() {
    val application = RuntimeEnvironment.getApplication()

    var errored = false
    LinkActions.openUrl(application, url) { errored = true }

    val intent = shadowOf(application).nextStartedActivity

    assertThat(errored).isFalse()
    assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
  }

  @Test
  fun `missing browser reports an error instead of throwing`() {
    val application = RuntimeEnvironment.getApplication()
    shadowOf(application).checkActivities(true)

    var error: LinkActions.OpenUrlError? = null
    LinkActions.openUrl(application, url) { error = it }

    assertThat(error == LinkActions.OpenUrlError.NoBrowserFound).isTrue()
  }
}

/**
 * [prepareExternalLink] is what the conversation screen uses to hand a tapped link to another app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class IntentExtensionsTest {

  @Test
  fun `a link handed to another app is given its own task`() {
    val intent = Intent(Intent.ACTION_VIEW, "https://x.com/someone/status/1".toUri()).prepareExternalLink()

    assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
  }

  @Test
  fun `a link handed to another app still asks the browser for a new tab`() {
    val intent = Intent(Intent.ACTION_VIEW, "https://signal.org/".toUri()).prepareExternalLink()

    assertThat(intent.getBooleanExtra(Browser.EXTRA_CREATE_NEW_TAB, false)).isTrue()
    assertThat(intent.getStringExtra(Browser.EXTRA_APPLICATION_ID) != null).isTrue()
  }
}
