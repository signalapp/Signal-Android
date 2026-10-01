/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview
import org.signal.mediasend.SentMediaQuality

class QualitySelectorSheetContentScreenshotTests {
  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun QualitySelectorSheetContentStandardPreview() {
    Previews.BottomSheetContentPreview {
      QualitySelectorSheetContent(quality = SentMediaQuality.STANDARD, onQualitySelected = {})
    }
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun QualitySelectorSheetContentHighPreview() {
    Previews.BottomSheetContentPreview {
      QualitySelectorSheetContent(quality = SentMediaQuality.HIGH, onQualitySelected = {})
    }
  }
}
