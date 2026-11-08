/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.EmptyLogger
import java.util.concurrent.TimeUnit

class RotateSenderCertificateListenerTest {

  companion object {
    private val INTERVAL = TimeUnit.DAYS.toMillis(1)
    private const val NOW = 1789734201707L
    private const val NEVER_ROTATED = -1L

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  @Test
  fun `given a rotation one interval ago, when resolving, then it is due now`() {
    assertThat(RotateSenderCertificateListener.resolveNextExecutionTime(NOW - INTERVAL, NOW)).isEqualTo(NOW)
  }

  @Test
  fun `given a rotation just now, when resolving, then it is due one interval out`() {
    assertThat(RotateSenderCertificateListener.resolveNextExecutionTime(NOW, NOW)).isEqualTo(NOW + INTERVAL)
  }

  @Test
  fun `given an overdue rotation, when resolving, then it is due in the past`() {
    assertThat(RotateSenderCertificateListener.resolveNextExecutionTime(NOW - (INTERVAL * 5), NOW)).isEqualTo(NOW - (INTERVAL * 4))
  }

  @Test
  fun `given no recorded rotation, when resolving, then it is due in the past`() {
    assertThat(RotateSenderCertificateListener.resolveNextExecutionTime(NEVER_ROTATED, NOW)).isEqualTo(NEVER_ROTATED + INTERVAL)
  }

  @Test
  fun `given a rotation barely in the future, when resolving, then it rotates now`() {
    assertThat(RotateSenderCertificateListener.resolveNextExecutionTime(NOW + 1, NOW)).isEqualTo(0)
  }

  @Test
  fun `given a rotation written by a bad boot clock, when resolving, then it rotates now`() {
    assertThat(RotateSenderCertificateListener.resolveNextExecutionTime(2279533251741L, NOW)).isEqualTo(0)
  }
}
