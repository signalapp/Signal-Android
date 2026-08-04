/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v2.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.mediasend.screens.edit.QualitySelectorSheetContent
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel

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
      Column {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
          BottomSheets.Handle(modifier = Modifier.padding(top = 6.dp))
        }

        QualitySelectorSheetContent(quality = quality, onQualitySelected = {
          sharedViewModel.setSentMediaQuality(it)
          dismiss()
        })
      }
    }
  }
}
