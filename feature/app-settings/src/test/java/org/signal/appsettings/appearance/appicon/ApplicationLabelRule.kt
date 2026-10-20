/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance.appicon

import android.app.Application
import androidx.annotation.StringRes
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/**
 * Gives the Robolectric application a launcher label, which presets that use the app's own name read from
 * `applicationInfo.labelRes`. A bare test application has none, so that lookup would throw.
 */
class ApplicationLabelRule(@StringRes private val labelRes: Int) : ExternalResource() {

  private var originalLabelRes = 0

  override fun before() {
    val applicationInfo = ApplicationProvider.getApplicationContext<Application>().applicationInfo
    originalLabelRes = applicationInfo.labelRes
    applicationInfo.labelRes = labelRes
  }

  override fun after() {
    ApplicationProvider.getApplicationContext<Application>().applicationInfo.labelRes = originalLabelRes
  }
}
