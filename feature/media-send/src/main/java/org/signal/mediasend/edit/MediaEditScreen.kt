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
import kotlinx.coroutines.launch
import org.signal.core.models.media.Media
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaSendNavKey
import org.signal.mediasend.MediaSendState
import org.signal.mediasend.goToSend

@Composable
fun MediaEditScreen(
  state: MediaSendState,
  callback: MediaEditScreenCallback,
  backStack: NavBackStack<NavKey>,
  videoEditorSlot: @Composable () -> Unit = {}
) {
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
    val isSmallWindowBreakpoint = rememberWindowBreakpoint() == WindowBreakpoint.SMALL
    val controllers = remember { EditorController.Container() }

    val currentController = state.focusedMedia?.let {
      when (val editorState = state.editorStateMap[it.uri]) {
        is EditorState.Image -> controllers.getOrCreateImageController(it.uri, editorState.model)
        is EditorState.VideoTrim -> EditorController.VideoTrim
        null -> error("Invalid editor state.")
      }
    }

    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize(),
      snapPosition = SnapPosition.Center,
      userScrollEnabled = currentController?.isUserInEdit != true
    ) { index ->
      val uri = state.selectedMedia[index].uri
      when (val editorState = state.editorStateMap[uri]) {
        is EditorState.Image -> {
          ImageEditor(
            controller = controllers.getOrCreateImageController(uri, editorState.model),
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

      when (currentController) {
        is EditorController.Image -> {
          if (isSmallWindowBreakpoint) {
            ImageEditorToolbar(imageEditorController = currentController)
          }
        }
        is EditorController.VideoTrim, null -> Unit
      }

      AddAMessageRow(
        message = state.message,
        callback = callback,
        onNextClick = { backStack.goToSend() },
        modifier = Modifier
          .widthIn(max = 624.dp)
          .padding(horizontal = 16.dp)
          .padding(bottom = 16.dp)
      )
    }

    if (!isSmallWindowBreakpoint && currentController is EditorController.Image) {
      ImageEditorToolbar(
        imageEditorController = currentController,
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
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
        focusedMedia = selectedMedia.first(),
        editorStateMap = mutableMapOf(
          selectedMedia.first().uri to EditorState.Image(EditorModel.create(0))
        )
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

interface MediaEditScreenCallback : AddAMessageRowCallback {
  fun setFocusedMedia(media: Media)

  object Empty : MediaEditScreenCallback, AddAMessageRowCallback by AddAMessageRowCallback.Empty {
    override fun setFocusedMedia(media: Media) = Unit
  }
}
