/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobmanager

import kotlinx.coroutines.runBlocking

/**
 * Perform a job utilizing operations that require coroutines. By default,
 * doRun is executed on the Default dispatcher.
 */
abstract class CoroutineJob(parameters: Parameters) : Job(parameters) {

  override fun run(): Result {
    return runBlocking {
      doRun()
    }
  }

  abstract suspend fun doRun(): Result
}
