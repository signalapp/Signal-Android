/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.giph.mp4

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.navigationBarsCompat
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.screens.gifsearch.GifSearch
import org.thoughtcrime.securesms.R

/**
 * Searches Giphy, handing a picked gif to the activity's [GiphyMp4ViewModel] to be saved.
 */
class GiphyMp4Fragment : ComposeFragment() {

  private val viewModel: GiphyMp4ViewModel by activityViewModels()

  @Composable
  override fun FragmentContent() {
    val keyboardController = LocalSoftwareKeyboardController.current

    GifSearch(
      repository = viewModel.gifKeyboardRepository,
      searchHint = stringResource(R.string.KeyboardPagerFragment_search_giphy),
      onAction = { action ->
        if (action is MediaKeyboardAction.GifSelected) {
          viewModel.gifKeyboardRepository.getGiphyImage(action.gif.id)?.let(viewModel::saveToBlob)
        }
      },
      onClose = {
        keyboardController?.hide()
        requireActivity().finish()
      },
      footer = { PoweredByGiphy() }
    )
  }
}

@Composable
private fun PoweredByGiphy() {
  Image(
    painter = painterResource(R.drawable.poweredby_giphy),
    contentDescription = null,
    modifier = Modifier
      .fillMaxWidth()
      .background(Color.Black)
      .windowInsetsPadding(WindowInsets.navigationBarsCompat.union(WindowInsets.ime))
      .padding(10.dp)
  )
}
