/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.permissions.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.R

/**
 * Dialogs and state management for permissions requests in compose screens.
 */
object Permissions {

  interface Controller {
    fun request()
  }

  private enum class RequestState {
    NONE,
    RATIONALE,
    SYSTEM
  }

  @Composable
  fun cameraPermissionHandler(
    rationale: String,
    onPermissionGranted: () -> Unit
  ): Controller {
    return permissionHandler(
      permission = android.Manifest.permission.CAMERA,
      icon = painterResource(id = R.drawable.symbol_camera_24),
      rationale = rationale,
      onPermissionGranted = onPermissionGranted
    )
  }

  /**
   * Generic permissions rationale dialog and state management for single permissions.
   */
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  fun permissionHandler(
    permission: String,
    icon: Painter,
    rationale: String,
    onPermissionGranted: () -> Unit
  ): Controller {
    var requestState by remember {
      mutableStateOf(RequestState.NONE)
    }

    val permissionState = rememberPermissionState(permission = permission) {
      if (it && requestState == RequestState.SYSTEM) {
        onPermissionGranted()
      }
    }

    if (requestState == RequestState.RATIONALE) {
      Dialogs.PermissionRationaleDialog(
        icon = icon,
        rationale = rationale,
        confirm = stringResource(id = R.string.Permissions_continue),
        dismiss = stringResource(id = R.string.Permissions_not_now),
        onConfirm = {
          requestState = RequestState.SYSTEM
          permissionState.launchPermissionRequest()
        },
        onDismiss = {
          requestState = RequestState.NONE
        }
      )
    }

    return object : Controller {
      override fun request() {
        if (permissionState.status.isGranted) {
          requestState = RequestState.NONE
          onPermissionGranted()
        } else {
          requestState = RequestState.RATIONALE
        }
      }
    }
  }
}
