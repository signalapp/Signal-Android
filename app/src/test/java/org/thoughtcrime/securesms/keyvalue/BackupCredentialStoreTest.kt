/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testutil.SignalStoreRule
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class BackupCredentialStoreTest {

  @get:Rule
  val signalStore = SignalStoreRule()

  private val today: Duration = 20_000.days

  @Test
  fun `clearOlderThan removes days before the current one and keeps today and later`() {
    val store = SignalStore.backup.messageCredentials
    store.add(listOf(credentialFor(today - 2.days), credentialFor(today - 1.days), credentialFor(today), credentialFor(today + 1.days)))

    store.clearOlderThan(today + 13.hours)

    assertThat(store.byDay.keys).containsExactlyInAnyOrder(today.inWholeSeconds, (today + 1.days).inWholeSeconds)
  }

  @Test
  fun `the credential for the current time survives a trim`() {
    val store = SignalStore.backup.messageCredentials
    val now = today + 23.hours
    store.add(listOf(credentialFor(today - 1.days), credentialFor(today)))

    store.clearOlderThan(now)

    assertThat(store.byDay.getForCurrentTime(now)).isNotNull().prop(ArchiveServiceCredential::redemptionTime).isEqualTo(today.inWholeSeconds)
    assertThat(store.byDay.getForCurrentTime(today - 1.days)).isNull()
  }

  private fun credentialFor(day: Duration): ArchiveServiceCredential {
    return ArchiveServiceCredential(ByteArray(16), day.inWholeSeconds)
  }
}
