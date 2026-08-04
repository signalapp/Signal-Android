/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.select

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.ActivityCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.signal.core.ui.permissions.PermissionDeniedSheet
import org.signal.core.ui.util.StorageUtil
import org.signal.core.util.permissions.PermissionCompat
import org.signal.mediasend.R

/**
 * Requests the read permissions the gallery needs from a coroutine: [request] suspends until the user resolves the
 * system prompt. The gallery's counterpart to [org.signal.core.ui.compose.PermissionController], which only speaks
 * for a single permission and treats "all granted" as the only success.
 *
 * The image and video permissions have to be requested as a set, and on Android 14+ the user can answer with
 * selected-photos access, which comes back as a denial of the broad permissions even though we gained access. So
 * [request] does not report what was granted: callers re-read what they can actually see via
 * [MediaPermissions.current], matching the `onAnyResult` handling in the v2 gallery.
 *
 * Hold an instance where a coroutine scope is available (typically a `ViewModel`) and render [Content] once in your
 * composition. [request] is single-shot: drive one request at a time per instance.
 */
@Stable
internal class MediaPermissionController {

  private var requestId: Int by mutableIntStateOf(0)
  private var denied: Boolean? by mutableStateOf(null)
  private var showSheetOnPermanentDenial: Boolean = false
  private var launchedRequestId: Int = 0

  /**
   * Prompts for the image and video read permissions and suspends until the user resolves the prompt.
   *
   * @param permanentDenialSheet offer a route into app settings if the system will no longer prompt. Only wanted for
   *   the up-front ask, since a user who already granted selected-photos access has a working path back.
   * @return whether the answer left us without the access we asked for, so the caller can say so. Selected-photos
   *   access is not a denial: it grants `READ_MEDIA_VISUAL_USER_SELECTED`, which is the only permission that counts
   *   towards a denial on Android 14+.
   */
  suspend fun request(permanentDenialSheet: Boolean): Boolean {
    showSheetOnPermanentDenial = permanentDenialSheet
    denied = null
    requestId++
    return snapshotFlow { denied }
      .filterNotNull()
      .first()
  }

  /** Hosts the launcher [request] drives. Call once in composition. */
  @OptIn(ExperimentalPermissionsApi::class)
  @Composable
  fun Content() {
    val activity = LocalActivity.current
    var showPermanentDenialSheet by rememberSaveable { mutableStateOf(false) }
    val permissions = remember { PermissionCompat.forImagesAndVideos().toList() }

    val permissionsState = rememberMultiplePermissionsState(permissions) { results ->
      // Driven off what we can actually read rather than the result map: selected-photos access denies the broad
      // permissions, and reporting that as a permanent denial would send a user who just granted access to settings.
      val stillLocked = !StorageUtil.canReadAnyFromMediaStore()
      val willNotPromptAgain = activity != null && permissions.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

      showPermanentDenialSheet = showSheetOnPermanentDenial && stillLocked && willNotPromptAgain
      denied = PermissionCompat.getRequiredPermissionsForDenial().all { results[it] == false }
    }

    // Guarded on launchedRequestId so a new composition cannot re-prompt for a request that has already been fired.
    LaunchedEffect(requestId) {
      if (requestId == 0 || requestId == launchedRequestId) {
        return@LaunchedEffect
      }
      launchedRequestId = requestId

      permissionsState.launchMultiplePermissionRequest()
    }

    if (showPermanentDenialSheet) {
      PermissionDeniedSheet(
        titleRes = R.string.MediaSelectScreen__allow_access_to_storage,
        subtitleRes = R.string.MediaSelectScreen__to_show_photos_and_videos,
        useExtended = true,
        onDismiss = { showPermanentDenialSheet = false }
      )
    }
  }
}
