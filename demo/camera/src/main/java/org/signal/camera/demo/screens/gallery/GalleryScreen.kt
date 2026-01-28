package org.signal.camera.demo.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import org.signal.camera.demo.Screen
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType

@Composable
fun GalleryScreen(
  backStack: NavBackStack<Screen>,
  viewModel: GalleryScreenViewModel = viewModel()
) {
  val context = LocalContext.current
  val state = viewModel.state.value
  var showDeleteDialog by remember { mutableStateOf(false) }
  
  LaunchedEffect(Unit) {
    viewModel.loadMedia(context)
  }
  
  // Delete confirmation dialog
  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text("Delete All Media?") },
      text = {
        Text("This will permanently delete all ${state.mediaItems.size} photos and videos from your gallery. This action cannot be undone.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteDialog = false
            viewModel.deleteAllMedia(context)
          },
          colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error
          )
        ) {
          Text("Delete All")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
  
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(WindowInsets.systemBars.asPaddingValues())
      .padding(start = 16.dp, end = 16.dp)
  ) {
    when {
      state.isLoading -> {
        CircularProgressIndicator(
          modifier = Modifier.align(Alignment.Center)
        )
      }
      state.error != null -> {
        Text(
          text = "Error: ${state.error}",
          modifier = Modifier
            .align(Alignment.Center)
            .padding(16.dp),
          color = MaterialTheme.colorScheme.error
        )
      }
      state.mediaItems.isEmpty() -> {
        Column(
          modifier = Modifier
            .align(Alignment.Center)
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "No photos or videos yet",
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = "Capture some photos to see them here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      else -> {
        LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.fillMaxSize()
        ) {
          items(state.mediaItems, key = { it.file.absolutePath }) { mediaItem ->
            MediaThumbnail(
              mediaItem = mediaItem,
              onClick = {
                org.signal.camera.demo.MediaSelectionHolder.selectedMedia = mediaItem
                when (mediaItem) {
                  is MediaItem.Image -> backStack.add(Screen.ImageViewer)
                  is MediaItem.Video -> backStack.add(Screen.VideoViewer)
                }
              }
            )
          }
        }
      }
    }
    
    // Delete all button at bottom (only show when there are items)
    if (state.mediaItems.isNotEmpty()) {
      Button(
        onClick = { showDeleteDialog = true },
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Delete All (${state.mediaItems.size})")
      }
    }
  }
}

@Composable
private fun MediaThumbnail(
  mediaItem: MediaItem,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .aspectRatio(1f)
      .clickable(onClick = onClick),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      GlideImage(
        model = mediaItem.file,
        scaleType = GlideImageScaleType.CENTER_CROP,
        modifier = Modifier.fillMaxSize()
      )
      
      if (mediaItem.isVideo) {
        Box(
          modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.3f)
            .aspectRatio(1f)
            .background(
              color = Color.Black.copy(alpha = 0.5f),
              shape = CircleShape
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Video",
            modifier = Modifier.fillMaxSize(0.7f),
            tint = Color.White
          )
        }
      }
    }
  }
}
