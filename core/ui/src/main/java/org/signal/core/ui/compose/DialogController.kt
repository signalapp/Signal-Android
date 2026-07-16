/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Drives a Compose dialog imperatively from a coroutine: [show] suspends until the user resolves the
 * dialog and returns the [DialogResult] they chose.
 *
 * Hold an instance where a coroutine scope is available (typically a `ViewModel`), render [Content]
 * once in your composition with the dialog UI, and call [show] from a coroutine to present it.
 * [Content] draws nothing until [show] is called and hides itself again once a result is produced.
 *
 * [show] is single-shot: drive one dialog at a time per instance. Concurrent callers share the same
 * visible state and all resolve to the same result.
 *
 * @param S the type of state passed to the dialog for rendering (e.g. a username to display). Use [Unit]
 *   when the dialog needs no state.
 *
 * Example:
 * ```
 * // 1. Hold an instance (e.g. on a ViewModel).
 * val usernameScannedDialog = DialogController<String>()
 *
 * // 2. Render its Content once, supplying the dialog UI.
 * usernameScannedDialog.Content { username, onDismissRequest, onConfirm, _, onDeny ->
 *   Dialogs.SimpleAlertDialog(
 *     title = stringResource(R.string.found_user, username),
 *     body = stringResource(R.string.start_chat_with, username),
 *     confirm = stringResource(R.string.go_to_chat),
 *     onConfirm = onConfirm,
 *     onDeny = onDeny,
 *     onDismissRequest = onDismissRequest
 *   )
 * }
 *
 * // 3. Show it from a coroutine and react to the result.
 * when (usernameScannedDialog.show(username)) {
 *   DialogResult.POSITIVE -> goToChat()
 *   else -> Unit
 * }
 * ```
 */
@Stable
class DialogController<S> {

  private var dialogState: S? by mutableStateOf(null)
  private var dialogResult: DialogResult? by mutableStateOf(null)
  private var visible: Boolean by mutableStateOf(false)

  /** Presents the dialog with the given [state] and suspends until the user resolves it. */
  suspend fun show(state: S): DialogResult {
    dialogState = state
    dialogResult = null
    visible = true
    return awaitDialogResult()
  }

  /**
   * Renders [dialog] while one is being shown via [show]; renders nothing otherwise. Call once in
   * composition.
   *
   * Invoke the callback matching the user's choice to resolve [show]: `onConfirm` → [DialogResult.POSITIVE],
   * `onNeutral` → [DialogResult.NEUTRAL], `onDeny`/`onDismissRequest` → [DialogResult.NEGATIVE].
   */
  @Composable
  fun Content(dialog: DialogContent<S>) {
    val state = dialogState
    if (visible && state != null) {
      dialog(
        state,
        { dialogResult = DialogResult.NEGATIVE },
        { dialogResult = DialogResult.POSITIVE },
        { dialogResult = DialogResult.NEUTRAL },
        { dialogResult = DialogResult.NEGATIVE }
      )
    }
  }

  private suspend fun awaitDialogResult(): DialogResult {
    return try {
      snapshotFlow { dialogResult }
        .filterNotNull()
        .first()
    } finally {
      // Hide the dialog on both normal resolution and cancellation, so a cancelled show()
      // can't leave it stuck visible with nobody left to resolve it.
      visible = false
    }
  }
}

/** The dialog UI rendered by [DialogController.Content]. */
typealias DialogContent<S> = @Composable (
  state: S,
  onDismissRequest: () -> Unit,
  onConfirm: () -> Unit,
  onNeutral: () -> Unit,
  onDeny: () -> Unit
) -> Unit

enum class DialogResult {
  POSITIVE,
  NEUTRAL,
  NEGATIVE
}
