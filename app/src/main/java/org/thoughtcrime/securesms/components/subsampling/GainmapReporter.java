/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.subsampling;

import androidx.annotation.AnyThread;

/**
 * Reports that a decoded preview image carried an UltraHDR gain map. Called from
 * SubsamplingScaleImageView's decode threads, possibly once per tile; implementations must be
 * idempotent and thread-safe.
 */
public interface GainmapReporter {
  @AnyThread
  void onGainmapPresent();
}
