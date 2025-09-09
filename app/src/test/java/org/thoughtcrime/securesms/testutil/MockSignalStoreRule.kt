/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.rules.ExternalResource
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.BackupValues
import org.thoughtcrime.securesms.keyvalue.EmojiValues
import org.thoughtcrime.securesms.keyvalue.InAppPaymentValues
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.keyvalue.RegistrationValues
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.SvrValues
import kotlin.reflect.KClass

/**
 * Mocks [SignalStore] to return mock versions of the various values. Mocks will default to not be relaxed (each
 * method call on them will need to be mocked) except for unit functions which will do nothing.
 *
 * Expand mocked values as necessary when needed.
 *
 * @param relaxed Set of value classes that should default to relaxed thus defaulting all methods. Useful
 * when value is not part of the input state under test but called within the under test code.
 */
@Suppress("MemberVisibilityCanBePrivate")
class MockSignalStoreRule(private val relaxed: Set<KClass<*>> = emptySet()) : ExternalResource() {

  lateinit var account: AccountValues
    private set

  lateinit var phoneNumberPrivacy: PhoneNumberPrivacyValues
    private set

  lateinit var registration: RegistrationValues
    private set

  lateinit var svr: SvrValues
    private set

  lateinit var emoji: EmojiValues
    private set

  lateinit var inAppPayments: InAppPaymentValues
    private set

  lateinit var backup: BackupValues
    private set

  lateinit var settings: SettingsValues
    private set

  override fun before() {
    account = mockk(relaxed = relaxed.contains(AccountValues::class), relaxUnitFun = true)
    phoneNumberPrivacy = mockk(relaxed = relaxed.contains(PhoneNumberPrivacyValues::class), relaxUnitFun = true)
    registration = mockk(relaxed = relaxed.contains(RegistrationValues::class), relaxUnitFun = true)
    svr = mockk(relaxed = relaxed.contains(SvrValues::class), relaxUnitFun = true)
    emoji = mockk(relaxed = relaxed.contains(EmojiValues::class), relaxUnitFun = true)
    inAppPayments = mockk(relaxed = relaxed.contains(InAppPaymentValues::class), relaxUnitFun = true)
    backup = mockk(relaxed = relaxed.contains(BackupValues::class), relaxUnitFun = true)
    settings = mockk(relaxed = relaxed.contains(SettingsValues::class), relaxUnitFun = true)

    mockkObject(SignalStore)
    every { SignalStore.account } returns account
    every { SignalStore.phoneNumberPrivacy } returns phoneNumberPrivacy
    every { SignalStore.registration } returns registration
    every { SignalStore.svr } returns svr
    every { SignalStore.emoji } returns emoji
    every { SignalStore.inAppPayments } returns inAppPayments
    every { SignalStore.backup } returns backup
    every { SignalStore.settings } returns settings
  }

  override fun after() {
    unmockkObject(SignalStore)
  }
}
