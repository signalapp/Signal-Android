/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.ContentTypeUtil
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaSendNavKey
import org.signal.mediasend.MediaSendState

@Composable
fun MediaEditScreen(
  state: MediaSendState,
  callback: MediaEditScreenCallback,
  backStack: NavBackStack<NavKey>,
  videoEditorSlot: @Composable () -> Unit = {}
) {
  val isFocusedMediaVideo = ContentTypeUtil.isVideoType(state.focusedMedia?.contentType)
  val scope = rememberCoroutineScope()

  val pagerState = rememberPagerState(
    initialPage = state.focusedMedia?.let { state.selectedMedia.indexOf(it) } ?: 0,
    pageCount = { state.selectedMedia.size }
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .navigationBarsPadding()
  ) {
    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize(),
      snapPosition = SnapPosition.Center
    ) { index ->
      when (val editorState = state.editorStateMap[state.selectedMedia[index].uri]) {
        is EditorState.Image -> {
          ImageEditor(
            controller = remember { ImageEditorController(editorState.model) },
            modifier = Modifier.fillMaxSize()
          )
        }

        is EditorState.VideoTrim -> {
          videoEditorSlot()
        }

        null -> {
          if (!LocalInspectionMode.current) {
            error("Invalid editor state.")
          } else {
            Box(modifier = Modifier.fillMaxSize().background(color = Previews.rememberRandomColor()))
          }
        }
      }
    }

    Column(
      verticalArrangement = spacedBy(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.align(Alignment.BottomCenter)
    ) {
      if (state.selectedMedia.isNotEmpty()) {
        ThumbnailRow(
          selectedMedia = state.selectedMedia,
          pagerState = pagerState,
          onFocusedMediaChange = callback::setFocusedMedia,
          onThumbnailClick = { index ->
            scope.launch {
              pagerState.animateScrollToPage(index)
            }
          }
        )
      }

      if (isFocusedMediaVideo) {
        // Video editor hud
      } else if (!currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        // Image editor HU
      }

      AddAMessageRow(
        message = state.message,
        callback = AddAMessageRowCallback.Empty,
        modifier = Modifier
          .widthIn(max = 624.dp)
          .padding(horizontal = 16.dp)
          .padding(bottom = 16.dp)
      )
    }
  }
}

@AllDevicePreviews
@Composable
private fun MediaEditScreenPreview() {
  val selectedMedia = rememberPreviewMedia(10)

  Previews.Preview {
    MediaEditScreen(
      state = MediaSendState(
        selectedMedia = selectedMedia,
        focusedMedia = selectedMedia.first()
      ),
      callback = MediaEditScreenCallback.Empty,
      backStack = rememberNavBackStack(MediaSendNavKey.Edit),
      videoEditorSlot = {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red)
        )
      }
    )
  }
}

interface MediaEditScreenCallback {
  fun setFocusedMedia(media: Media)

  object Empty : MediaEditScreenCallback {
    override fun setFocusedMedia(media: Media) = Unit
  }
}
