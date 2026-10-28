/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.util.getParcelableExtraCompat
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.util.RemoteConfig

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppSettingsActivityIntentsTest {

  private val context: Application = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    mockkObject(RemoteConfig)
  }

  @After
  fun tearDown() {
    unmockkObject(RemoteConfig)
  }

  @Test
  fun `subscriptions opens recurring checkout in the fragment host`() {
    every { RemoteConfig.useAppSettingsNavigation } returns false

    val intent = AppSettingsActivity.subscriptions(context)

    assertThat(intent.component?.className).isEqualTo(AppSettingsActivity::class.java.name)
    assertThat(intent.startRoute()).isEqualTo(AppSettingsRoute.DonationsRoute.Donations(directToCheckoutType = InAppPaymentType.RECURRING_DONATION))
  }

  @Test
  fun `subscriptions opens recurring checkout in the Nav3 host`() {
    every { RemoteConfig.useAppSettingsNavigation } returns true

    val intent = AppSettingsActivity.subscriptions(context)

    assertThat(intent.component?.className).isEqualTo(AppSettingsNavigationActivity::class.java.name)
    assertThat(intent.startRoute()).isEqualTo(AppSettingsRoute.DonationsRoute.Donations(directToCheckoutType = InAppPaymentType.RECURRING_DONATION))
  }

  @Test
  fun `boost opens one-time checkout`() {
    every { RemoteConfig.useAppSettingsNavigation } returns false

    val intent = AppSettingsActivity.boost(context)

    assertThat(intent.startRoute()).isEqualTo(AppSettingsRoute.DonationsRoute.Donations(directToCheckoutType = InAppPaymentType.ONE_TIME_DONATION))
  }

  /** The start route rides under a host-specific extra key, so find it by type rather than by name. */
  private fun Intent.startRoute(): AppSettingsRoute? {
    return extras?.keySet()?.firstNotNullOfOrNull { getParcelableExtraCompat(it, AppSettingsRoute::class.java) }
  }
}
