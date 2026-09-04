/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.startsWith
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.appsettings.totp.TotpApp
import org.signal.core.models.MasterKey
import org.signal.libsignal.net.ConfirmedMfaKey
import org.signal.libsignal.net.MfaKeyKind
import org.signal.libsignal.net.MfaKeyNotFoundException
import org.signal.libsignal.net.MfaMetadata
import org.signal.libsignal.net.OneTimePasswordNotVerifiedException
import org.signal.libsignal.net.PendingTotpKey
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.TooManyMfaKeysException
import org.signal.libsignal.net.TooManyTotpKeysException
import org.signal.libsignal.net.TotpParameters
import org.signal.network.api.AccountApiV2
import org.thoughtcrime.securesms.components.settings.app.account.authenticator.TotpRepository.AppsResult
import org.thoughtcrime.securesms.components.settings.app.account.authenticator.TotpRepository.BeginSetupResult
import org.thoughtcrime.securesms.components.settings.app.account.authenticator.TotpRepository.ConfirmResult
import org.thoughtcrime.securesms.components.settings.app.account.authenticator.TotpRepository.UpdateResult
import java.io.IOException
import java.time.Duration
import java.time.Instant

class TotpRepositoryTest {

  companion object {
    private const val NOW = 1_700_000_000_000L
    private const val ACCOUNT_NAME = "8B4A1F0C"
    private const val CODE = "123456"
    private const val KEY_ID = 1

    private val KEY = ByteArray(32) { it.toByte() }
    private val MASTER_KEY = MasterKey(ByteArray(32) { (it + 100).toByte() })

    /** What the service generates: a 256-bit key with HMAC-SHA1, six digits, thirty second steps. */
    private val PARAMETERS = TotpParameters(algorithm = "HmacSHA1", passwordLength = 6, timeStep = Duration.ofSeconds(30))
    private val PENDING_KEY = PendingTotpKey(key = KEY, parameters = PARAMETERS)
  }

  private var now = NOW
  private val api = mockk<AccountApiV2>()
  private val repository = TotpRepository(api = api, masterKeyProvider = { MASTER_KEY }, clock = { now })

  @Before
  fun setUp() {
    coEvery { api.generateTotpKey() } returns RequestResult.Success(PENDING_KEY)
    coEvery { api.confirmTotpKey(any(), any(), any()) } returns RequestResult.Success(KEY_ID)
    coEvery { api.listMfaKeys(any()) } returns RequestResult.Success(emptyList())
    coEvery { api.setMfaKeyMetadata(any(), any(), any()) } returns RequestResult.Success(Unit)
    coEvery { api.removeMfaKey(any()) } returns RequestResult.Success(Unit)
  }

  @Test
  fun `beginSetup returns a link and a key in both the forms the screen needs`() = runTest {
    val result = repository.beginSetup(ACCOUNT_NAME) as BeginSetupResult.Success

    assertThat(result.setupUri).startsWith("otpauth://totp/Signal:$ACCOUNT_NAME?")
    assertThat(result.setupUri).contains("secret=${result.clipboardKey}")
    assertThat(result.displayKey).isEqualTo(result.clipboardKey.chunked(4).joinToString(" "))
  }

  /** The issuer and the account name have to differ, or an app that shows both renders "Signal: Signal". */
  @Test
  fun `beginSetup names the entry after the account, under the issuer`() = runTest {
    val result = repository.beginSetup(ACCOUNT_NAME) as BeginSetupResult.Success

    assertThat(result.setupUri).startsWith("otpauth://totp/Signal:$ACCOUNT_NAME?")
    assertThat(result.setupUri).contains("issuer=Signal&")
  }

  /** Nothing should reach this without an ACI, but a bare issuer beats a label ending in a colon if anything does. */
  @Test
  fun `beginSetup falls back to the issuer alone when there's no account name`() = runTest {
    val result = repository.beginSetup("") as BeginSetupResult.Success

    assertThat(result.setupUri).startsWith("otpauth://totp/Signal?")
  }

  @Test
  fun `beginSetup treats an account name of nothing but whitespace as no account name`() = runTest {
    val result = repository.beginSetup("   ") as BeginSetupResult.Success

    assertThat(result.setupUri).startsWith("otpauth://totp/Signal?")
  }

  /** A space has to become %20 rather than the + form encoding would produce, or apps render it literally. */
  @Test
  fun `beginSetup percent-encodes the account name`() = runTest {
    val result = repository.beginSetup("+1 555") as BeginSetupResult.Success

    assertThat(result.setupUri).startsWith("otpauth://totp/Signal:%2B1%20555?")
  }

  @Test
  fun `beginSetup writes out the parameters the service chose`() = runTest {
    val result = repository.beginSetup(ACCOUNT_NAME) as BeginSetupResult.Success

    assertThat(result.setupUri).contains("algorithm=SHA1")
    assertThat(result.setupUri).contains("digits=6")
    assertThat(result.setupUri).contains("period=30")
  }

  /** A link naming an algorithm the format doesn't define would pair an app whose codes never confirm, which is worse than not starting. */
  @Test
  fun `a key with parameters a setup link can't describe fails setup rather than being handed out`() = runTest {
    coEvery { api.generateTotpKey() } returns RequestResult.Success(
      PendingTotpKey(key = KEY, parameters = TotpParameters(algorithm = "HmacMD5", passwordLength = 6, timeStep = Duration.ofSeconds(30)))
    )

    assertThat(repository.beginSetup(ACCOUNT_NAME)).isEqualTo(BeginSetupResult.NetworkFailure)
  }

  @Test
  fun `an account at its TOTP key limit is told rather than handed a key`() = runTest {
    coEvery { api.generateTotpKey() } returns RequestResult.NonSuccess(TooManyTotpKeysException("full"))

    assertThat(repository.beginSetup(ACCOUNT_NAME)).isEqualTo(BeginSetupResult.TooManyApps)
  }

  @Test
  fun `an account at its overall MFA key limit is told rather than handed a key`() = runTest {
    coEvery { api.generateTotpKey() } returns RequestResult.NonSuccess(TooManyMfaKeysException("full"))

    assertThat(repository.beginSetup(ACCOUNT_NAME)).isEqualTo(BeginSetupResult.TooManyApps)
  }

  @Test
  fun `a service we couldn't reach fails setup`() = runTest {
    coEvery { api.generateTotpKey() } returns RequestResult.RetryableNetworkError(IOException("offline"))

    assertThat(repository.beginSetup(ACCOUNT_NAME)).isEqualTo(BeginSetupResult.NetworkFailure)
  }

  @Test
  fun `a code that isn't a number is reported as a wrong code without asking the service`() = runTest {
    assertThat(repository.confirmPendingApp("abcdef")).isEqualTo(ConfirmResult.IncorrectCode)

    coVerify(exactly = 0) { api.confirmTotpKey(any(), any(), any()) }
  }

  @Test
  fun `a code the service rejects is reported as a wrong code`() = runTest {
    coEvery { api.confirmTotpKey(any(), any(), any()) } returns RequestResult.NonSuccess(OneTimePasswordNotVerifiedException("nope"))

    assertThat(repository.confirmPendingApp(CODE)).isEqualTo(ConfirmResult.IncorrectCode)
  }

  @Test
  fun `an account that filled up while the key was pending is told so`() = runTest {
    coEvery { api.confirmTotpKey(any(), any(), any()) } returns RequestResult.NonSuccess(TooManyMfaKeysException("full"))

    assertThat(repository.confirmPendingApp(CODE)).isEqualTo(ConfirmResult.TooManyApps)
  }

  @Test
  fun `a code the service accepts confirms the app`() = runTest {
    val result = repository.confirmPendingApp(CODE)

    assertThat(result).isInstanceOf(ConfirmResult.Success::class)
    assertThat((result as ConfirmResult.Success).appId).isEqualTo(KEY_ID.toLong())
  }

  /** The service wants metadata at confirmation time, and the user hasn't been asked for a name yet. */
  @Test
  fun `a key is confirmed without a name, stamped with the time it was confirmed`() = runTest {
    val metadata = slot<MfaMetadata>()
    coEvery { api.confirmTotpKey(any(), capture(metadata), any()) } returns RequestResult.Success(KEY_ID)

    repository.confirmPendingApp(CODE)

    assertThat(metadata.captured.name).isEqualTo("")
    assertThat(metadata.captured.createdAt).isEqualTo(Instant.ofEpochMilli(NOW))
  }

  @Test
  fun `the confirmed keys on the account come back as apps`() = runTest {
    coEvery { api.listMfaKeys(any()) } returns RequestResult.Success(
      listOf(
        ConfirmedMfaKey(id = KEY_ID, metadata = MfaMetadata(name = "Aegis", createdAt = Instant.ofEpochMilli(NOW)), kind = MfaKeyKind.TOTP)
      )
    )

    val apps = (repository.getTotpApps() as AppsResult.Success).apps

    assertThat(apps).hasSize(1)
    assertThat(apps.first().id).isEqualTo(KEY_ID.toLong())
    assertThat(apps.first().name).isEqualTo("Aegis")
    assertThat(apps.first().createdAt).isEqualTo(NOW)
  }

  /** Metadata we can't read was written under some other key, so listing it as a nameless app would be worse than omitting it. */
  @Test
  fun `a key whose metadata can't be read is left out of the list`() = runTest {
    coEvery { api.listMfaKeys(any()) } returns RequestResult.Success(
      listOf(ConfirmedMfaKey(id = KEY_ID, metadata = null, kind = MfaKeyKind.TOTP))
    )

    assertThat((repository.getTotpApps() as AppsResult.Success).apps).isEmpty()
  }

  /** The list is about what's on the account, not what this client understands, so a newer device's key still shows. */
  @Test
  fun `a key of a kind this client doesn't know still shows up as an app`() = runTest {
    coEvery { api.listMfaKeys(any()) } returns RequestResult.Success(
      listOf(ConfirmedMfaKey(id = KEY_ID, metadata = MfaMetadata(name = "Future", createdAt = Instant.ofEpochMilli(NOW)), kind = MfaKeyKind.UNKNOWN))
    )

    assertThat((repository.getTotpApps() as AppsResult.Success).apps).hasSize(1)
  }

  @Test
  fun `a list we couldn't fetch is a network failure`() = runTest {
    coEvery { api.listMfaKeys(any()) } returns RequestResult.RetryableNetworkError(IOException("offline"))

    assertThat(repository.getTotpApps()).isEqualTo(AppsResult.NetworkFailure)
  }

  @Test
  fun `naming a new app stamps it with the current time`() = runTest {
    val metadata = slot<MfaMetadata>()
    coEvery { api.setMfaKeyMetadata(eq(KEY_ID), capture(metadata), any()) } returns RequestResult.Success(Unit)

    assertThat(repository.nameNewTotpApp(KEY_ID.toLong(), "Aegis")).isEqualTo(UpdateResult.Success)

    assertThat(metadata.captured.name).isEqualTo("Aegis")
    assertThat(metadata.captured.createdAt).isEqualTo(Instant.ofEpochMilli(NOW))
  }

  @Test
  fun `renaming keeps the time the app was confirmed`() = runTest {
    val metadata = slot<MfaMetadata>()
    coEvery { api.setMfaKeyMetadata(eq(KEY_ID), capture(metadata), any()) } returns RequestResult.Success(Unit)
    val app = TotpApp(id = KEY_ID.toLong(), name = "Aegis", createdAt = NOW)
    now += 60_000

    assertThat(repository.renameTotpApp(app, "Aegis on my tablet")).isEqualTo(UpdateResult.Success)

    assertThat(metadata.captured.name).isEqualTo("Aegis on my tablet")
    assertThat(metadata.captured.createdAt).isEqualTo(Instant.ofEpochMilli(NOW))
  }

  @Test
  fun `renaming an app the service no longer has is reported as not found`() = runTest {
    coEvery { api.setMfaKeyMetadata(any(), any(), any()) } returns RequestResult.NonSuccess(MfaKeyNotFoundException("gone"))
    val gone = TotpApp(id = 7, name = "Aegis", createdAt = NOW)

    assertThat(repository.renameTotpApp(gone, "Aegis on my tablet")).isEqualTo(UpdateResult.AppNotFound)
  }

  @Test
  fun `removing an app removes its key`() = runTest {
    assertThat(repository.removeTotpApp(KEY_ID.toLong())).isEqualTo(UpdateResult.Success)

    coVerify { api.removeMfaKey(KEY_ID) }
  }

  @Test
  fun `a removal we couldn't send is a network failure`() = runTest {
    coEvery { api.removeMfaKey(any()) } returns RequestResult.RetryableNetworkError(IOException("offline"))

    assertThat(repository.removeTotpApp(KEY_ID.toLong())).isEqualTo(UpdateResult.NetworkFailure)
  }
}
