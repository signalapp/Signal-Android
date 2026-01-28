package org.signal.camera.demo.screens.imageviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import org.signal.camera.demo.Screen
import org.signal.camera.demo.screens.gallery.MediaItem
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType

@Composable
fun ImageViewerScreen(
  backStack: NavBackStack<Screen>
) {
  val selectedMedia = org.signal.camera.demo.MediaSelectionHolder.selectedMedia
  
  if (selectedMedia == null || selectedMedia !is MediaItem.Image) {
    // No image selected, go back
    backStack.removeLastOrNull()
    return
  }
  
  val imageFile = selectedMedia.file
  
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(WindowInsets.systemBars.asPaddingValues())
      .background(Color.Black)
  ) {
    // Image with pinch-to-zoom
    Box(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
          detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            if (scale > 1f) {
              offset += pan
            } else {
              offset = Offset.Zero
            }
          }
        },
      contentAlignment = Alignment.Center
    ) {
      GlideImage(
        model = imageFile,
        scaleType = GlideImageScaleType.FIT_CENTER,
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y
          )
      )
    }
    
    // Back button
    IconButton(
      onClick = { backStack.removeLastOrNull() },
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
