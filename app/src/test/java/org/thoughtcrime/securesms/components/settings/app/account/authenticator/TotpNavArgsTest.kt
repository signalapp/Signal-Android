/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import android.app.Application
import android.os.Bundle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.totp.TotpApp

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TotpNavArgsTest {

  companion object {
    private val APP = TotpApp(id = 7, name = "Aegis", createdAt = 1_700_000_000_000L)
  }

  @Test
  fun `no arguments means the screen is acting on a newly paired app`() {
    assertThat(TotpNavArgs.appId(null)).isNull()
    assertThat(TotpNavArgs.renamedApp(null)).isNull()
  }

  @Test
  fun `an unset app id reads as null`() {
    val arguments = Bundle().apply { putLong(TotpNavArgs.ARG_APP_ID, TotpNavArgs.NO_APP_ID) }

    assertThat(TotpNavArgs.appId(arguments)).isNull()
  }

  @Test
  fun `a newly paired app carries its id but no renamed app`() {
    val arguments = Bundle().apply {
      putLong(TotpNavArgs.ARG_APP_ID, 7)
      putBoolean(TotpNavArgs.ARG_RENAMING, false)
      putString(TotpNavArgs.ARG_APP_NAME, "")
      putLong(TotpNavArgs.ARG_APP_CREATED_AT, TotpNavArgs.NO_CREATED_AT)
    }

    assertThat(TotpNavArgs.appId(arguments)).isEqualTo(7L)
    assertThat(TotpNavArgs.renamedApp(arguments)).isNull()
  }

  @Test
  fun `a rename carries the whole app the list already had`() {
    val arguments = Bundle().apply { TotpNavArgs.putRenamedApp(this, APP) }

    assertThat(TotpNavArgs.appId(arguments)).isEqualTo(APP.id)
    assertThat(TotpNavArgs.renamedApp(arguments)).isEqualTo(APP)
  }

  @Test
  fun `a rename of an app whose metadata couldn't be read carries no name or date`() {
    val app = TotpApp(id = 7, name = null, createdAt = null)
    val arguments = Bundle().apply { TotpNavArgs.putRenamedApp(this, app) }

    assertThat(TotpNavArgs.renamedApp(arguments)).isEqualTo(app)
  }
}
