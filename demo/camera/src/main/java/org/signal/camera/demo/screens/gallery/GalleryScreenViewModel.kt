package org.signal.camera.demo.screens.gallery

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "GalleryScreenViewModel"
private const val GALLERY_FOLDER = "gallery"

class GalleryScreenViewModel : ViewModel() {
  private val _state: MutableState<GalleryScreenState> = mutableStateOf(GalleryScreenState())
  val state: State<GalleryScreenState>
    get() = _state
  
  fun loadMedia(context: Context) {
    _state.value = _state.value.copy(isLoading = true, error = null)
    
    viewModelScope.launch {
      try {
        val mediaItems = loadMediaFromInternalStorage(context)
        _state.value = _state.value.copy(
          mediaItems = mediaItems,
          isLoading = false
        )
        Log.d(TAG, "Loaded ${mediaItems.size} media items")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load media: ${e.message}", e)
        _state.value = _state.value.copy(
          isLoading = false,
          error = e.message ?: "Unknown error"
        )
      }
    }
  }
  
  fun deleteAllMedia(context: Context) {
    viewModelScope.launch {
      try {
        val count = deleteAllMediaFromInternalStorage(context)
        Log.d(TAG, "Deleted $count media items")
        // Reload to update UI
        loadMedia(context)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to delete media: ${e.message}", e)
        _state.value = _state.value.copy(
          error = e.message ?: "Failed to delete media"
        )
      }
    }
  }
  
  private suspend fun loadMediaFromInternalStorage(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
    val galleryDir = File(context.filesDir, GALLERY_FOLDER)
    
    if (!galleryDir.exists()) {
      return@withContext emptyList()
    }
    
    galleryDir.listFiles()
      ?.filter { it.isFile }
      ?.mapNotNull { file ->
        when {
          file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp") -> {
            MediaItem.Image(file)
          }
          file.extension.lowercase() in listOf("mp4", "mkv", "webm", "mov") -> {
            MediaItem.Video(file)
          }
          else -> null
        }
      }
      ?.sortedByDescending { it.lastModified }
      ?: emptyList()
  }
  
  private suspend fun deleteAllMediaFromInternalStorage(context: Context): Int = withContext(Dispatchers.IO) {
    val galleryDir = File(context.filesDir, GALLERY_FOLDER)
    
    if (!galleryDir.exists()) {
      return@withContext 0
    }
    
    val files = galleryDir.listFiles() ?: return@withContext 0
    var deletedCount = 0
    
    files.forEach { file ->
      if (file.isFile && file.delete()) {
        deletedCount++
      }
    }
    
    deletedCount
  }
}
