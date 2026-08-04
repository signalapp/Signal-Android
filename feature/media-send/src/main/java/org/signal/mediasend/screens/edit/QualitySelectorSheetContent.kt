/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.dismissWithAnimation
import org.signal.mediasend.R
import org.signal.mediasend.SentMediaQuality

/**
 * Modal bottom sheet offering the media quality options, dismissing itself once one is picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QualitySelectorBottomSheet(
  quality: SentMediaQuality,
  onQualitySelected: (SentMediaQuality) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  val scope = rememberCoroutineScope()

  BottomSheets.BottomSheet(
    onDismissRequest = { sheetState.dismissWithAnimation(scope, onComplete = onDismiss) },
    sheetState = sheetState
  ) {
    QualitySelectorSheetContent(
      quality = quality,
      onQualitySelected = { selected ->
        sheetState.dismissWithAnimation(scope) {
          onDismiss()
          onQualitySelected(selected)
        }
      }
    )
  }
}

/**
 * Bottom sheet content for selecting the media quality (Standard vs. High) when sending media. The drag handle belongs
 * to the sheet hosting this, so that a modal sheet can supply its own.
 */
@Composable
fun QualitySelectorSheetContent(
  quality: SentMediaQuality,
  onQualitySelected: (SentMediaQuality) -> Unit
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = stringResource(id = R.string.QualitySelectorBottomSheetDialog__media_quality),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(top = 20.dp, bottom = 14.dp)
    )
    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 20.dp)
    ) {
      val standardQuality = quality == SentMediaQuality.STANDARD
      Button(
        modifier = Modifier
          .defaultMinSize(minWidth = 174.dp, minHeight = 60.dp)
          .weight(1f),
        onClick = { onQualitySelected(SentMediaQuality.STANDARD) },
        shape = RoundedCornerShape(percent = 25),
        colors = if (standardQuality) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.textButtonColors(),
        elevation = if (standardQuality) ButtonDefaults.filledTonalButtonElevation() else null,
        contentPadding = if (standardQuality) ButtonDefaults.ContentPadding else ButtonDefaults.TextButtonContentPadding
      ) {
        ButtonLabel(title = stringResource(id = R.string.QualitySelectorBottomSheetDialog__standard), description = stringResource(id = R.string.QualitySelectorBottomSheetDialog__faster_less_data))
      }
      Button(
        modifier = Modifier
          .defaultMinSize(minWidth = 174.dp, minHeight = 60.dp)
          .weight(1f),
        onClick = { onQualitySelected(SentMediaQuality.HIGH) },
        shape = RoundedCornerShape(percent = 25),
        colors = if (!standardQuality) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.textButtonColors(),
        elevation = if (!standardQuality) ButtonDefaults.filledTonalButtonElevation() else null,
        contentPadding = if (!standardQuality) ButtonDefaults.ContentPadding else ButtonDefaults.TextButtonContentPadding
      ) {
        ButtonLabel(title = stringResource(id = R.string.QualitySelectorBottomSheetDialog__high), description = stringResource(id = R.string.QualitySelectorBottomSheetDialog__slower_more_data))
      }
    }
  }
}

@Composable
private fun ButtonLabel(title: String, description: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = title, color = MaterialTheme.colorScheme.onSurface)
    Text(text = description, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
  }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun QualitySelectorSheetContentStandardPreview() {
  Previews.Preview {
    QualitySelectorSheetContent(SentMediaQuality.STANDARD) {}
  }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun QualitySelectorSheetContentHighPreview() {
  Previews.Preview {
    QualitySelectorSheetContent(SentMediaQuality.HIGH) {}
  }
}
