/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.signal.core.ui.R
import org.signal.core.ui.permissions.Permissions as PermissionsUtil

/**
 * Requests a runtime permission imperatively from a coroutine: [request] suspends until the user resolves the
 * system prompt and returns whether the permission was granted. The counterpart to [DialogController], for a
 * permission needed part-way through a flow the caller is already suspended in. Use [Permissions.permissionHandler]
 * instead when the prompt should be preceded by a rationale dialog.
 *
 * Hold an instance where a coroutine scope is available (typically a `ViewModel`) and render [Content] once in
 * your composition. [request] is single-shot: drive one request at a time per instance.
 *
 * @param permanentDenialMessage explains what the permission is needed for. Shown, alongside a route into the app's
 *   settings, when the permission is permanently denied and the system will no longer prompt. The Compose equivalent
 *   of [PermissionsUtil.PermissionsBuilder.withPermanentDenialDialog].
 */
@Stable
class PermissionController(
  private val permission: String,
  @StringRes private val permanentDenialMessage: Int? = null
) {

  private var requestId: Int by mutableIntStateOf(0)
  private var granted: Boolean? by mutableStateOf(null)
  private var launchedRequestId: Int = 0

  /** Prompts for the permission and suspends until the user resolves it. */
  suspend fun request(): Boolean {
    granted = null
    requestId++
    return snapshotFlow { granted }
      .filterNotNull()
      .first()
  }

  /** Hosts the launcher [request] drives. Call once in composition. */
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  fun Content() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var showPermanentDenialDialog by rememberSaveable { mutableStateOf(false) }

    val permissionState = rememberPermissionState(permission = permission) { isGranted ->
      // The system stops prompting once a permission is permanently denied, so app settings is the only way back.
      if (!isGranted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
        showPermanentDenialDialog = permanentDenialMessage != null
      }

      granted = isGranted
    }

    // Guarded on launchedRequestId so a new composition cannot re-prompt for a request that has already been fired.
    LaunchedEffect(requestId) {
      if (requestId == 0 || requestId == launchedRequestId) {
        return@LaunchedEffect
      }
      launchedRequestId = requestId

      if (permissionState.status.isGranted) {
        granted = true
      } else {
        permissionState.launchPermissionRequest()
      }
    }

    if (showPermanentDenialDialog && permanentDenialMessage != null) {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.Permissions_permission_required),
        body = stringResource(permanentDenialMessage),
        confirm = stringResource(R.string.Permissions_continue),
        onConfirm = { context.startActivity(PermissionsUtil.getApplicationSettingsIntent(context)) },
        dismiss = stringResource(android.R.string.cancel),
        onDismiss = { showPermanentDenialDialog = false }
      )
    }
  }
}
