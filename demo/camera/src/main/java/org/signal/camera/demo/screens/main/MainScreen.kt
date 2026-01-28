@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.camera.demo.screens.main

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.NavBackStack
import org.signal.camera.demo.Screen
import org.signal.camera.hud.StandardCameraHudEvents
import org.signal.camera.CameraScreen
import org.signal.camera.CameraScreenEvents
import org.signal.camera.CameraScreenViewModel
import org.signal.camera.hud.StandardCameraHud

@Composable
fun MainScreen(
  backStack: NavBackStack<Screen>,
  viewModel: MainScreenViewModel = viewModel(),
) {
  val cameraViewModel: CameraScreenViewModel = viewModel()

  val permissions = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.RECORD_AUDIO)
    
    if (Build.VERSION.SDK_INT >= 33) {
      add(Manifest.permission.READ_MEDIA_IMAGES)
      add(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
      add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
  }
  
  val permissionsState = rememberMultiplePermissionsState(permissions = permissions)

  val context = LocalContext.current
  var qrCodeContent by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    permissionsState.launchMultiplePermissionRequest()
  }

  // Observe save status and show toasts
  LaunchedEffect(viewModel.state.value.saveStatus) {
    viewModel.state.value.saveStatus?.let { status ->
      val message = when (status) {
        is SaveStatus.Saving -> null
        is SaveStatus.Success -> "Saved to gallery!"
        is SaveStatus.Error -> "Failed to save: ${status.message}"
      }
      message?.let {
        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        viewModel.onEvent(MainScreenEvents.ClearSaveStatus)
      }
    }
  }

  // Observe QR code detections from the camera view model
  LaunchedEffect(cameraViewModel) {
    cameraViewModel.qrCodeDetected.collect { qrCode ->
      qrCodeContent = qrCode
    }
  }

  when {
    permissionsState.allPermissionsGranted -> {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black),
        contentAlignment = Alignment.Center
      ) {
        CameraScreen(
          state = cameraViewModel.state.value,
          emitter = { event -> cameraViewModel.onEvent(event) }
        ) {
          StandardCameraHud(
            state = cameraViewModel.state.value,
            emitter = { event ->
              when (event) {
                is StandardCameraHudEvents.PhotoCaptureTriggered -> {
                  cameraViewModel.capturePhoto(
                    context = context,
                    onPhotoCaptured = { bitmap ->
                      viewModel.onEvent(MainScreenEvents.SavePhoto(context, bitmap))
                    }
                  )
                }
                is StandardCameraHudEvents.VideoCaptureStarted -> {
                  cameraViewModel.startRecording(
                    context = context,
                    output = viewModel.createVideoOutput(context),
                    onVideoCaptured = { result ->
                      viewModel.onEvent(MainScreenEvents.VideoSaved(result))
                    }
                  )
                }
                is StandardCameraHudEvents.VideoCaptureStopped-> {
                  cameraViewModel.stopRecording()
                }
                is StandardCameraHudEvents.GalleryClick -> {
                  backStack.add(Screen.Gallery)
                }
                is StandardCameraHudEvents.ToggleFlash -> {
                  cameraViewModel.onEvent(CameraScreenEvents.NextFlashMode)
                }
                is StandardCameraHudEvents.ClearCaptureError -> {
                  cameraViewModel.onEvent(CameraScreenEvents.ClearCaptureError)
                }
                is StandardCameraHudEvents.SwitchCamera -> {
                  cameraViewModel.onEvent(CameraScreenEvents.SwitchCamera(context))
                }
                is StandardCameraHudEvents.SetZoomLevel -> {
                  cameraViewModel.onEvent(CameraScreenEvents.LinearZoom(event.zoomLevel))
                }
                is StandardCameraHudEvents.MediaSelectionClick -> {
                  // Doesn't need to be handled
                }
              }
            }
          )
        }
      }
      
      // QR Code Dialog
      if (qrCodeContent != null) {
        AlertDialog(
          onDismissRequest = { qrCodeContent = null },
          title = { Text("QR Code Detected") },
          text = { Text(qrCodeContent ?: "") },
          confirmButton = {
            TextButton(onClick = { qrCodeContent = null }) {
              Text("OK")
            }
          }
        )
      }
    }
    else -> {
      PermissionDeniedContent(permissionsState)
    }
  }
}

@Composable
fun PermissionDeniedContent(permissionsState: MultiplePermissionsState) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(16.dp)
    ) {
      Text(
        text = "Camera, microphone, and media permissions are required",
        style = MaterialTheme.typography.bodyLarge
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Media permissions allow showing your recent photos in the gallery button",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(16.dp))
      Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
        Text("Grant Permissions")
      }
    }
  }
}

@PreviewLightDark
@Composable
fun PreviewPermissionDeniedLight() {
  MaterialTheme {
    Surface(modifier = Modifier.fillMaxSize()) {
      PermissionDeniedContent(permissionsState = PreviewMultiplePermissionsState())
    }
  }
}

private class PreviewMultiplePermissionsState : MultiplePermissionsState {
  override val allPermissionsGranted: Boolean = false
  override val permissions: List<PermissionState> = emptyList()
  override val revokedPermissions: List<PermissionState> = emptyList()
  override val shouldShowRationale: Boolean = false
  override fun launchMultiplePermissionRequest() {}
}
