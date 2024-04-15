/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobmanager

import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil
import org.thoughtcrime.securesms.util.FeatureFlags

/**
 * Helper to calculate the default backoff interval for a [Job] given it's run attempt count.
 */
fun Job.defaultBackoffInterval(): Long = BackoffUtil.exponentialBackoff(runAttempt + 1, FeatureFlags.getDefaultMaxBackoff())
