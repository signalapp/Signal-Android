/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.util

import android.app.Application
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log.initialize
import org.thoughtcrime.securesms.assertIs
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.LogRecorder
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.util.RemoteConfig

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class RegistrationUtilTest {

  @get:Rule
  val signalStore = MockSignalStoreRule(relaxed = setOf(PhoneNumberPrivacyValues::class))

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var logRecorder: LogRecorder

  @Before
  fun setup() {
    mockkObject(Recipient)
    mockkObject(RemoteConfig)
    every { RemoteConfig.init() } just Runs

    logRecorder = LogRecorder()
    initialize(logRecorder)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun maybeMarkRegistrationComplete_allValidNoRestoreOption() {
    every { signalStore.registration.isRegistrationComplete } returns false
    every { signalStore.account.isRegistered } returns true
    every { Recipient.self() } returns Recipient(profileName = ProfileName.fromParts("Dark", "Helmet"))
    every { signalStore.svr.hasOptedInWithAccess() } returns true
    every { RemoteConfig.restoreAfterRegistration } returns false

    RegistrationUtil.maybeMarkRegistrationComplete()

    verify { signalStore.registration.markRegistrationComplete() }
  }

  @Test
  fun maybeMarkRegistrationComplete_allValidNoRestoreOptionSvrOptOut() {
    every { signalStore.registration.isRegistrationComplete } returns false
    every { signalStore.account.isRegistered } returns true
    every { Recipient.self() } returns Recipient(profileName = ProfileName.fromParts("Dark", "Helmet"))
    every { signalStore.svr.hasOptedInWithAccess() } returns false
    every { signalStore.svr.hasOptedOut() } returns true
    every { RemoteConfig.restoreAfterRegistration } returns false

    RegistrationUtil.maybeMarkRegistrationComplete()

    verify { signalStore.registration.markRegistrationComplete() }
  }

  @Test
  fun maybeMarkRegistrationComplete_allValidWithRestoreOption() {
    every { signalStore.registration.isRegistrationComplete } returns false
    every { signalStore.account.isRegistered } returns true
    every { Recipient.self() } returns Recipient(profileName = ProfileName.fromParts("Dark", "Helmet"))
    every { signalStore.svr.hasOptedInWithAccess() } returns true
    every { RemoteConfig.restoreAfterRegistration } returns true
    every { signalStore.registration.hasSkippedTransferOrRestore() } returns true

    RegistrationUtil.maybeMarkRegistrationComplete()

    verify { signalStore.registration.markRegistrationComplete() }
  }

  @Test
  fun maybeMarkRegistrationComplete_missingData() {
    every { signalStore.registration.isRegistrationComplete } returns false
    every { signalStore.account.isRegistered } returns false

    RegistrationUtil.maybeMarkRegistrationComplete()

    every { signalStore.account.isRegistered } returns true
    every { Recipient.self() } returns Recipient(profileName = ProfileName.EMPTY)

    RegistrationUtil.maybeMarkRegistrationComplete()

    every { Recipient.self() } returns Recipient(profileName = ProfileName.fromParts("Dark", "Helmet"))
    every { signalStore.svr.hasOptedInWithAccess() } returns false
    every { signalStore.svr.hasOptedOut() } returns false

    RegistrationUtil.maybeMarkRegistrationComplete()

    every { signalStore.svr.hasOptedInWithAccess() } returns true
    every { RemoteConfig.restoreAfterRegistration } returns true
    every { signalStore.registration.hasSkippedTransferOrRestore() } returns false
    every { signalStore.registration.hasCompletedRestore() } returns false

    RegistrationUtil.maybeMarkRegistrationComplete()

    verify(exactly = 0) { signalStore.registration.markRegistrationComplete() }

    val regUtilLogs = logRecorder.information.filter { it.tag == "RegistrationUtil" }
    regUtilLogs.size assertIs 4
    regUtilLogs.all { it.message == "Registration is not yet complete." } assertIs true
  }

  @Test
  fun maybeMarkRegistrationComplete_alreadyMarked() {
    every { signalStore.registration.isRegistrationComplete } returns true

    RegistrationUtil.maybeMarkRegistrationComplete()

    verify(exactly = 0) { signalStore.registration.markRegistrationComplete() }

    val regUtilLogs = logRecorder.information.filter { it.tag == "RegistrationUtil" }
    regUtilLogs.size assertIs 0
  }
}
