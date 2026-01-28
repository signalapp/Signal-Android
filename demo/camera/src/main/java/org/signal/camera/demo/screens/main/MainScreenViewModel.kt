package org.signal.camera.demo.screens.main

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.signal.camera.VideoCaptureResult
import org.signal.camera.VideoOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "MainScreenViewModel"
private const val GALLERY_FOLDER = "gallery"

class MainScreenViewModel : ViewModel() {
  private val _state: MutableState<MainScreenState> = mutableStateOf(MainScreenState())
  val state: State<MainScreenState>
    get() = _state

  fun onEvent(event: MainScreenEvents) {
    val currentState = _state.value
    when (event) {
      is MainScreenEvents.SavePhoto -> {
        handleSavePhotoEvent(currentState, event)
      }
      is MainScreenEvents.VideoSaved -> {
        handleVideoSavedEvent(currentState, event)
      }
      is MainScreenEvents.ClearSaveStatus -> {
        handleClearSaveStatusEvent(currentState, event)
      }
    }
  }
  
  fun createVideoOutput(context: Context): VideoOutput {
    // Create gallery directory in internal storage
    val galleryDir = File(context.filesDir, GALLERY_FOLDER)
    if (!galleryDir.exists()) {
      galleryDir.mkdirs()
    }
    
    // Generate filename with timestamp
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
      .format(System.currentTimeMillis())
    val file = File(galleryDir, "$name.mp4")
    
    // Open the file as a ParcelFileDescriptor
    val fileDescriptor = ParcelFileDescriptor.open(
      file,
      ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
    )
    
    return VideoOutput.FileDescriptorOutput(fileDescriptor)
  }

  private fun handleSavePhotoEvent(
    state: MainScreenState,
    event: MainScreenEvents.SavePhoto
  ) {
    _state.value = state.copy(saveStatus = SaveStatus.Saving)

    viewModelScope.launch {
      try {
        saveBitmapToMediaStore(event)
        _state.value = _state.value.copy(saveStatus = SaveStatus.Success)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to save photo: ${e.message}", e)
        _state.value = _state.value.copy(saveStatus = SaveStatus.Error(e.message))
      }
    }
  }

  private fun handleVideoSavedEvent(
    state: MainScreenState,
    event: MainScreenEvents.VideoSaved
  ) {
    when (val result = event.result) {
      is VideoCaptureResult.Success -> {
        // Close the file descriptor now that recording is complete
        result.fileDescriptor?.close()
        
        Log.d(TAG, "Video saved successfully")
        _state.value = state.copy(saveStatus = SaveStatus.Success)
      }
      is VideoCaptureResult.Error -> {
        Log.e(TAG, "Failed to save video: ${result.message}", result.throwable)
        _state.value = state.copy(saveStatus = SaveStatus.Error(result.message))
      }
    }
  }
  
  private fun handleClearSaveStatusEvent(
    state: MainScreenState,
    event: MainScreenEvents.ClearSaveStatus
  ) {
    _state.value = state.copy(saveStatus = null)
  }

  private suspend fun saveBitmapToMediaStore(event: MainScreenEvents.SavePhoto) = withContext(Dispatchers.IO) {
    // Create gallery directory in internal storage
    val galleryDir = File(event.context.filesDir, "gallery")
    if (!galleryDir.exists()) {
      galleryDir.mkdirs()
    }
    
    // Generate filename with timestamp
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
      .format(System.currentTimeMillis())
    val file = File(galleryDir, "$name.jpg")
    
    // Save bitmap to file
    file.outputStream().use { outputStream ->
      event.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
      Log.d(TAG, "Photo saved to internal storage: ${file.absolutePath}")
    }
  }
}
