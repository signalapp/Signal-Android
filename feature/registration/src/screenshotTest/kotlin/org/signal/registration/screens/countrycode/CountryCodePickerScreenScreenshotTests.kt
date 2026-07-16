/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.countrycode

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview

class CountryCodePickerScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun CountryCodePickerScreenPreview() {
    Previews.Preview {
      CountryCodePickerScreen(
        state = CountryCodeState(
          countryList = mutableListOf(
            Country("🇺🇸", "United States", 1, "US"),
            Country("🇨🇦", "Canada", 2, "CA"),
            Country("🇲🇽", "Mexico", 3, "MX")
          ),
          commonCountryList = mutableListOf(
            Country("🇺🇸", "United States", 4, "US"),
            Country("🇨🇦", "Canada", 5, "CA")
          )
        ),
        onEvent = {}
      )
    }
  }
}
