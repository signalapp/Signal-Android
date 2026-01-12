/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.quality

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.horizontalGutters
import org.signal.storageservice.protos.calls.quality.SubmitCallQualitySurveyRequest
import org.thoughtcrime.securesms.R

@Composable
fun CallQualityDiagnosticsScreen(
  callQualitySurveyRequest: SubmitCallQualitySurveyRequest,
  onNavigationClick: () -> Unit = {}
) {
  Scaffolds.Settings(
    title = stringResource(R.string.CallQualityDiagnosticsScreen__diagnostic_information),
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    navigationContentDescription = stringResource(R.string.CallQualityDiagnosticsScreen__close),
    onNavigationClick = onNavigationClick
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(it)
        .fillMaxSize()
        .horizontalGutters()
    ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
      ) {
        Text(
          text = callQualitySurveyRequest.toString().substringAfter(SubmitCallQualitySurveyRequest::class.simpleName ?: "")
        )
      }

      Buttons.LargeTonal(
        onClick = onNavigationClick,
        modifier = Modifier
          .padding(top = 10.dp, bottom = 24.dp)
          .widthIn(min = 256.dp)
      ) {
        Text(text = stringResource(R.string.CallQualityDiagnosticsScreen__close))
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun CallQualityDiagnosticsScreenPreview() {
  Previews.Preview {
    CallQualityDiagnosticsScreen(
      callQualitySurveyRequest = SubmitCallQualitySurveyRequest()
    )
  }
}
