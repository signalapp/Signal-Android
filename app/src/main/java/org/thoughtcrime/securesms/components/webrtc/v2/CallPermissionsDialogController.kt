/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.Manifest
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.permissions.PermissionDeniedBottomSheet.Companion.showPermissionFragment
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.util.BottomSheetUtil

/**
 * Shared dialog controller for requesting different permissions specific to calling.
 */
class CallPermissionsDialogController {

  var isAskingForPermission: Boolean = false
    private set

  fun requestCameraPermission(
    activity: AppCompatActivity,
    onAllGranted: Runnable
  ) {
    if (!isAskingForPermission) {
      isAskingForPermission = true
      Permissions.with(activity)
        .request(Manifest.permission.CAMERA)
        .ifNecessary()
        .withRationaleDialog(activity.getString(R.string.WebRtcCallActivity__allow_access_camera), activity.getString(R.string.WebRtcCallActivity__to_enable_video_allow_camera), false, R.drawable.symbol_video_24)
        .onAnyResult { isAskingForPermission = false }
        .onAllGranted(onAllGranted)
        .onAnyDenied { Toast.makeText(activity, R.string.WebRtcCallActivity__signal_needs_camera_access_enable_video, Toast.LENGTH_LONG).show() }
        .onAnyPermanentlyDenied {
          showPermissionFragment(R.string.WebRtcCallActivity__allow_access_camera, R.string.WebRtcCallActivity__to_enable_video, false)
            .show(activity.supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
        }
        .execute()
    }
  }

  fun requestAudioPermission(
    activity: AppCompatActivity,
    onGranted: Runnable,
    onDenied: Runnable
  ) {
    if (!isAskingForPermission) {
      isAskingForPermission = true
      Permissions.with(activity)
        .request(Manifest.permission.RECORD_AUDIO)
        .ifNecessary()
        .withRationaleDialog(activity.getString(R.string.WebRtcCallActivity__allow_access_microphone), activity.getString(R.string.WebRtcCallActivity__to_start_call_microphone), false, R.drawable.ic_mic_24)
        .onAnyResult { isAskingForPermission = false }
        .onAllGranted(onGranted)
        .onAnyDenied {
          Toast.makeText(activity, R.string.WebRtcCallActivity__signal_needs_microphone_start_call, Toast.LENGTH_LONG).show()
          onDenied.run()
        }
        .onAnyPermanentlyDenied {
          showPermissionFragment(R.string.WebRtcCallActivity__allow_access_microphone, R.string.WebRtcCallActivity__to_start_call, false)
            .show(activity.supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
        }
        .execute()
    }
  }

  fun requestCameraAndAudioPermission(
    activity: AppCompatActivity,
    onAllGranted: Runnable,
    onCameraGranted: Runnable,
    onAudioDenied: Runnable
  ) {
    if (!isAskingForPermission) {
      isAskingForPermission = true
      Permissions.with(activity)
        .request(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        .ifNecessary()
        .withRationaleDialog(activity.getString(R.string.WebRtcCallActivity__allow_access_camera_microphone), activity.getString(R.string.WebRtcCallActivity__to_start_call_camera_microphone), false, R.drawable.ic_mic_24, R.drawable.symbol_video_24)
        .onAnyResult { isAskingForPermission = false }
        .onSomePermanentlyDenied { deniedPermissions: List<String?> ->
          if (deniedPermissions.containsAll(listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))) {
            showPermissionFragment(R.string.WebRtcCallActivity__allow_access_camera_microphone, R.string.WebRtcCallActivity__to_start_call, false)
              .show(activity.supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
          } else if (deniedPermissions.contains(Manifest.permission.CAMERA)) {
            showPermissionFragment(R.string.WebRtcCallActivity__allow_access_camera, R.string.WebRtcCallActivity__to_enable_video, false)
              .show(activity.supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
          } else {
            showPermissionFragment(R.string.WebRtcCallActivity__allow_access_microphone, R.string.WebRtcCallActivity__to_start_call, false)
              .show(activity.supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
          }
        }
        .onAllGranted(onAllGranted)
        .onSomeGranted { permissions: List<String?> ->
          if (permissions.contains(Manifest.permission.CAMERA)) {
            onCameraGranted.run()
          }
        }
        .onSomeDenied { deniedPermissions: List<String?> ->
          if (deniedPermissions.contains(Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(activity, R.string.WebRtcCallActivity__signal_needs_microphone_start_call, Toast.LENGTH_LONG).show()
            onAudioDenied.run()
          } else {
            Toast.makeText(activity, R.string.WebRtcCallActivity__signal_needs_camera_access_enable_video, Toast.LENGTH_LONG).show()
          }
        }
        .execute()
    }
  }
}
