package org.signal.camera.demo.screens.main

import android.content.Context
import android.graphics.Bitmap

sealed interface MainScreenEvents {
  data class SavePhoto(val context: Context, val bitmap: Bitmap) : MainScreenEvents
  
  data class VideoSaved(val result: org.signal.camera.VideoCaptureResult) : MainScreenEvents
  
  data object ClearSaveStatus : MainScreenEvents
}
