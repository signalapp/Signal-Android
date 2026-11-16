/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.twofactor

import android.app.Application
import android.os.Bundle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.account.TwoFactorMethod

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TwoFactorNavArgsTest {

  companion object {
    private const val CREATED_AT = 1_700_000_000_000L
    private val APP = TwoFactorMethod(id = 7, kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, name = "Aegis", createdAt = CREATED_AT)
    private val PASSKEY = TwoFactorMethod(id = 7, kind = TwoFactorMethod.Kind.PASSKEY, name = "Pixel Phone", createdAt = CREATED_AT)
  }

  @Test
  fun `no arguments means the screen is acting on a newly added method`() {
    assertThat(TwoFactorNavArgs.methodId(null)).isNull()
    assertThat(TwoFactorNavArgs.renamedMethod(null)).isNull()
  }

  @Test
  fun `an unset method id reads as null`() {
    val arguments = Bundle().apply { putLong(TwoFactorNavArgs.ARG_METHOD_ID, TwoFactorNavArgs.NO_METHOD_ID) }

    assertThat(TwoFactorNavArgs.methodId(arguments)).isNull()
  }

  @Test
  fun `a newly added method carries its id and kind but nothing to rename`() {
    val arguments = Bundle().apply {
      TwoFactorNavArgs.putNewMethod(this, TwoFactorMethod(id = 7, kind = TwoFactorMethod.Kind.PASSKEY, name = null, createdAt = CREATED_AT))
      putBoolean(TwoFactorNavArgs.ARG_RENAMING, false)
      putString(TwoFactorNavArgs.ARG_METHOD_NAME, "")
    }

    assertThat(TwoFactorNavArgs.methodId(arguments)).isEqualTo(7L)
    assertThat(TwoFactorNavArgs.methodKind(arguments)).isEqualTo(TwoFactorMethod.Kind.PASSKEY)
    assertThat(TwoFactorNavArgs.createdAt(arguments)).isEqualTo(CREATED_AT)
    assertThat(TwoFactorNavArgs.renamedMethod(arguments)).isNull()
  }

  @Test
  fun `a rename carries the whole method the list already had`() {
    val arguments = Bundle().apply { TwoFactorNavArgs.putRenamedMethod(this, APP) }

    assertThat(TwoFactorNavArgs.methodId(arguments)).isEqualTo(APP.id)
    assertThat(TwoFactorNavArgs.renamedMethod(arguments)).isEqualTo(APP)
  }

  /** Ids are only unique within a kind, so the kind has to survive the trip or a passkey renames an app. */
  @Test
  fun `a renamed passkey keeps its kind`() {
    val arguments = Bundle().apply { TwoFactorNavArgs.putRenamedMethod(this, PASSKEY) }

    assertThat(TwoFactorNavArgs.renamedMethod(arguments)).isEqualTo(PASSKEY)
  }

  /** A method whose metadata we couldn't read has no name and no date, but is still a rename rather than a new method. */
  @Test
  fun `a rename of a method whose metadata couldn't be read carries no name or date`() {
    val method = TwoFactorMethod(id = 7, kind = TwoFactorMethod.Kind.PASSKEY, name = null, createdAt = null)
    val arguments = Bundle().apply { TwoFactorNavArgs.putRenamedMethod(this, method) }

    assertThat(TwoFactorNavArgs.renamedMethod(arguments)).isEqualTo(method)
  }

  /** A kind added by a newer build has to read as something this one can still show and remove. */
  @Test
  fun `an unrecognized kind falls back to an authenticator app`() {
    val arguments = Bundle().apply { putString(TwoFactorNavArgs.ARG_METHOD_KIND, "SOMETHING_NEW") }

    assertThat(TwoFactorNavArgs.methodKind(arguments)).isEqualTo(TwoFactorMethod.Kind.AUTHENTICATOR_APP)
  }
}
