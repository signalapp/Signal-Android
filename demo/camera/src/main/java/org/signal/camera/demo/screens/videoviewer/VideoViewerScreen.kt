package org.signal.camera.demo.screens.videoviewer

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation3.runtime.NavBackStack
import org.signal.camera.demo.Screen
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun VideoViewerScreen(
  backStack: NavBackStack<Screen>
) {
  val selectedMedia = org.signal.camera.demo.MediaSelectionHolder.selectedMedia
  
  if (selectedMedia == null || selectedMedia !is org.signal.camera.demo.screens.gallery.MediaItem.Video) {
    // No video selected, go back
    backStack.removeAt(backStack.lastIndex)
    return
  }
  
  val context = LocalContext.current
  val videoFile = selectedMedia.file
  
  val exoPlayer = remember {
    ExoPlayer.Builder(context).build().apply {
      val mediaItem = MediaItem.fromUri(videoFile.toURI().toString())
      setMediaItem(mediaItem)
      prepare()
      playWhenReady = true
      repeatMode = Player.REPEAT_MODE_OFF
    }
  }
  
  DisposableEffect(Unit) {
    onDispose {
      exoPlayer.release()
    }
  }
  
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    // Video player
    AndroidView(
      factory = { ctx ->
        PlayerView(ctx).apply {
          player = exoPlayer
          layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          useController = true
          controllerShowTimeoutMs = 3000
        }
      },
      modifier = Modifier.fillMaxSize()
    )
    
    // Back button
    IconButton(
      onClick = { backStack.removeAt(backStack.lastIndex) },
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(8.dp)
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        tint = Color.White
      )
    }
  }
}
