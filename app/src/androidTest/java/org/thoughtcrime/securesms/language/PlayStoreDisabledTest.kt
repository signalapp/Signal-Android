/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.language

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.PlayServicesUtil
import org.signal.core.util.PlayStoreUtil
import org.signal.locale.LanguageAvailability
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * Turns the Play Store and Google Play services off for real and checks what the language picker is told. Needs a split
 * install without [LANGUAGE] on a device where Play can deliver it. Both packages are turned back on after each test.
 */
@RunWith(AndroidJUnit4::class)
class PlayStoreDisabledTest {

  companion object {
    private const val LANGUAGE = "it"
  }

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun setUp() {
    LanguageSplitTestSetup.requireSplitInstall()
    LanguageSplitTestSetup.requireDownloadable(LANGUAGE)
  }

  @After
  fun tearDown() {
    LanguageSplitTestSetup.shell("pm enable ${PlayStoreUtil.PLAY_STORE_PACKAGE}")
    LanguageSplitTestSetup.shell("pm enable ${PlayServicesUtil.PLAY_SERVICES_PACKAGE}")
  }

  @Test
  fun disabledPlayStoreBlocksDownloadUntilEnabled() {
    LanguageSplitTestSetup.shell("pm disable-user --user 0 ${PlayStoreUtil.PLAY_STORE_PACKAGE}")

    assertThat(AppDependencies.languageSplitRepository.availability(LANGUAGE)).isEqualTo(LanguageAvailability.PlayStoreDisabled)
    assertThat(context.packageManager.resolveActivity(playStoreSettingsIntent(), 0), name = "Play Store settings").isNotNull()

    LanguageSplitTestSetup.shell("pm enable ${PlayStoreUtil.PLAY_STORE_PACKAGE}")

    assertThat(AppDependencies.languageSplitRepository.availability(LANGUAGE)).isEqualTo(LanguageAvailability.NeedsDownload)
  }

  @Test
  fun disabledPlayServicesBlocksDownload() {
    LanguageSplitTestSetup.shell("pm disable-user --user 0 ${PlayServicesUtil.PLAY_SERVICES_PACKAGE}")

    assertThat(AppDependencies.languageSplitRepository.availability(LANGUAGE)).isEqualTo(LanguageAvailability.PlayServicesDisabled)
  }

  private fun playStoreSettingsIntent(): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", PlayStoreUtil.PLAY_STORE_PACKAGE, null))
  }
}
