/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.permissions.PermissionDeniedSheet
import org.signal.mediasend.R
import org.signal.core.ui.R as CoreUiR
import org.signal.core.ui.permissions.Permissions as PermissionsUtil

/**
 * The camera and microphone permission requests that [CameraXScreen] drives.
 *
 * [CameraXFragment] does this work for the fragment-hosted flow through the callback-based permission builder. The
 * Compose-hosted flow has no fragment to route those callbacks through, so this is its counterpart; the two are meant
 * to stay in step.
 */
@Stable
internal class CameraPermissionController(
  /** Whether the camera can be opened at all. Polled by [CameraXScreen], so it reads through to the system each time. */
  val hasCameraPermission: () -> Boolean,
  /** Asks for everything capture needs that is still missing. A no-op once the camera has been granted. */
  val requestCapturePermissions: () -> Unit,
  /** Asks for the microphone on its own, for a recording that was started without it. */
  val requestMicrophonePermission: () -> Unit
)

/**
 * Builds the [CameraPermissionController] for a camera screen, and emits the rationale dialog and permanent-denial
 * sheets that go with it. Call once in the composition hosting the camera.
 *
 * @param isVideoEnabled whether recording is offered, which is what makes the microphone worth asking for up front.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun rememberCameraPermissionController(isVideoEnabled: Boolean): CameraPermissionController {
  // Accompanist registers activity result launchers, which a preview has nothing to register against. Reporting the
  // camera as granted is also what lets a preview render the viewfinder rather than the permission interstitial.
  if (LocalInspectionMode.current) {
    return remember {
      CameraPermissionController(
        hasCameraPermission = { true },
        requestCapturePermissions = {},
        requestMicrophonePermission = {}
      )
    }
  }

  val context = LocalContext.current
  val activity = LocalActivity.current

  var deniedPermission: DeniedPermission? by rememberSaveable { mutableStateOf(null) }
  var showMicrophoneRationale by rememberSaveable { mutableStateOf(false) }

  // The system stops prompting once a permission is permanently denied, which leaves app settings as the only way back.
  val isPermanentlyDenied: (String) -> Boolean = { permission ->
    activity != null &&
      !PermissionsUtil.hasAll(context, permission) &&
      !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
  }

  val capturePermissions = remember(isVideoEnabled) {
    if (isVideoEnabled) {
      listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    } else {
      listOf(Manifest.permission.CAMERA)
    }
  }

  val captureState = rememberMultiplePermissionsState(capturePermissions) {
    // Read back through the system rather than off the result map: anything already granted is absent from it.
    when {
      PermissionsUtil.hasAll(context, Manifest.permission.CAMERA) -> Unit

      isPermanentlyDenied(Manifest.permission.CAMERA) -> {
        deniedPermission = if (isVideoEnabled && isPermanentlyDenied(Manifest.permission.RECORD_AUDIO)) {
          DeniedPermission.CAMERA_AND_MICROPHONE
        } else {
          DeniedPermission.CAMERA
        }
      }

      else -> Toast.makeText(context, R.string.CameraXFragment_signal_needs_camera_access_capture_photos, Toast.LENGTH_LONG).show()
    }
  }

  val microphoneState = rememberPermissionState(Manifest.permission.RECORD_AUDIO) {
    when {
      PermissionsUtil.hasAll(context, Manifest.permission.RECORD_AUDIO) -> Unit
      isPermanentlyDenied(Manifest.permission.RECORD_AUDIO) -> deniedPermission = DeniedPermission.MICROPHONE
      else -> Toast.makeText(context, R.string.CameraXFragment_signal_needs_microphone_access_video, Toast.LENGTH_LONG).show()
    }
  }

  // Only the standalone microphone ask is prefaced by a rationale: the user has already started a recording by then, so
  // the prompt needs to explain why it is interrupting them. The up-front camera ask is self-evident from the screen.
  if (showMicrophoneRationale) {
    Dialogs.PermissionRationaleDialog(
      icon = SignalIcons.Mic.painter,
      rationale = stringResource(R.string.CameraXFragment_to_capture_videos_with_sound),
      confirm = stringResource(CoreUiR.string.Permissions_continue),
      dismiss = stringResource(CoreUiR.string.Permissions_not_now),
      onConfirm = {
        showMicrophoneRationale = false
        microphoneState.launchPermissionRequest()
      },
      onDismiss = { showMicrophoneRationale = false }
    )
  }

  deniedPermission?.let { denied ->
    PermissionDeniedSheet(
      titleRes = denied.titleRes,
      subtitleRes = denied.subtitleRes,
      onDismiss = { deniedPermission = null }
    )
  }

  return remember(captureState, microphoneState) {
    CameraPermissionController(
      hasCameraPermission = { PermissionsUtil.hasAll(context, Manifest.permission.CAMERA) },
      requestCapturePermissions = {
        if (!PermissionsUtil.hasAll(context, Manifest.permission.CAMERA)) {
          captureState.launchMultiplePermissionRequest()
        }
      },
      requestMicrophonePermission = {
        if (!microphoneState.status.isGranted) {
          showMicrophoneRationale = true
        }
      }
    )
  }
}

/**
 * The permanently denied permission a sheet is offered for, and the copy naming what it is needed for.
 */
private enum class DeniedPermission(
  @param:StringRes val titleRes: Int,
  @param:StringRes val subtitleRes: Int
) {
  CAMERA(
    titleRes = R.string.CameraXFragment_allow_access_camera,
    subtitleRes = R.string.CameraXFragment_to_capture_photos_videos
  ),
  CAMERA_AND_MICROPHONE(
    titleRes = R.string.CameraXFragment_allow_access_camera_microphone,
    subtitleRes = R.string.CameraXFragment_to_capture_photos_videos
  ),
  MICROPHONE(
    titleRes = R.string.CameraXFragment_allow_access_microphone,
    subtitleRes = R.string.CameraXFragment_to_capture_videos
  )
}
