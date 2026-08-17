/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.subsampling;

import androidx.annotation.AnyThread;

/**
 * Callback used by the preview decoders to report that the image they just decoded carries an
 * UltraHDR gain map. Called from SubsamplingScaleImageView's decode worker threads, possibly many
 * times (once per tile); implementations must be idempotent and thread-safe.
 */
public interface GainmapReporter {
  /**
   * Reports that a decoded bitmap carried an UltraHDR gain map.
   */
  @AnyThread
  void onGainmapPresent();
}
