/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v2.review

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel
import org.thoughtcrime.securesms.mms.SentMediaQuality

/**
 * Bottom sheet dialog to select the media quality (Standard vs. High) when sending media.
 */
class QualitySelectorBottomSheet : ComposeBottomSheetDialogFragment() {
  private val sharedViewModel: MediaSelectionViewModel by viewModels(ownerProducer = { requireActivity() })

  override val forceDarkTheme = true

  @Composable
  override fun SheetContent() {
    val state by sharedViewModel.state.observeAsState()
    val quality = state?.quality
    if (quality != null) {
      Content(quality = quality, onQualitySelected = {
        sharedViewModel.setSentMediaQuality(it)
        dismiss()
      })
    }
  }
}

@Composable
private fun Content(quality: SentMediaQuality, onQualitySelected: (SentMediaQuality) -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
      BottomSheets.Handle(modifier = Modifier.padding(top = 6.dp))
    }

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
private fun PreviewQualitySelectorBottomSheetStandard() {
  Previews.Preview {
    Content(SentMediaQuality.STANDARD) {}
  }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewQualitySelectorBottomSheetHigh() {
  Previews.Preview {
    Content(SentMediaQuality.HIGH) {}
  }
}
