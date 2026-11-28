/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video

import android.net.Uri

/** A [VideoEditorViewModel.Command] for the player of the video at [uri]. */
data class VideoPlayerCommand(val uri: Uri, val command: VideoEditorViewModel.Command)
