/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.twofactorselection

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.signal.registration.RegistrationRoute

class TwoFactorMethodTest {

  private val parameters = WebAuthnParameters(challenge = byteArrayOf(1, 2, 3), timeoutSeconds = 60, allowedCredentialIds = listOf(byteArrayOf(4, 5, 6)))
  private val passkey = TwoFactorMethod.Passkey(parameters)

  @Test
  fun `a lone passkey goes to passkey verification`() {
    assertThat(listOf(passkey).toAuthenticationRoute()).isEqualTo(RegistrationRoute.PasskeyVerification(parameters))
  }

  @Test
  fun `only authenticator apps go to code entry`() {
    assertThat(listOf(TwoFactorMethod.AuthenticatorApp).toAuthenticationRoute()).isEqualTo(RegistrationRoute.TotpEntry)
  }

  @Test
  fun `a passkey alongside an authenticator app goes to the selection screen`() {
    val methods = listOf(passkey, TwoFactorMethod.AuthenticatorApp)

    assertThat(methods.toAuthenticationRoute()).isEqualTo(RegistrationRoute.TwoFactorSelection(methods))
  }

  @Test
  fun `more than one passkey goes to the selection screen`() {
    val methods = listOf(passkey, passkey)

    assertThat(methods.toAuthenticationRoute()).isEqualTo(RegistrationRoute.TwoFactorSelection(methods))
  }
}
