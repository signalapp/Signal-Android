/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.signal.core.models.media.MediaFolder

internal fun NavBackStack<NavKey>.goToEdit() {
  goToSingle(MediaSendNavKey.Edit)
}

internal fun NavBackStack<NavKey>.goToSend() {
  goToSingle(MediaSendNavKey.Send)
}

internal fun NavBackStack<NavKey>.goToFolders() {
  goToSingle(MediaSendNavKey.Select.Folders)
}

internal fun NavBackStack<NavKey>.goToFiles(mediaFolder: MediaFolder) {
  add(MediaSendNavKey.Select.Files(mediaFolder))
}

internal fun NavBackStack<NavKey>.goToTextStory() {
  goToSingle(MediaSendNavKey.Capture.TextStory)
}

internal fun NavBackStack<NavKey>.goToCamera() {
  goToSingle(MediaSendNavKey.Capture.Camera)
}

internal fun NavBackStack<NavKey>.pop() {
  if (isNotEmpty()) {
    removeAt(size - 1)
  }
}

private fun NavBackStack<NavKey>.goToSingle(key: NavKey) {
  if (contains(key)) {
    popTo(key)
  } else {
    add(key)
  }
}

private fun NavBackStack<NavKey>.popTo(key: NavKey) {
  while (size > 1 && get(size - 1) != key) {
    removeAt(size - 1)
  }
}
