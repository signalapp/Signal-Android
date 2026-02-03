/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.signal.imageeditor.core.model.EditorModel

@Stable
class ImageEditorController @RememberInComposition constructor(
  val editorModel: EditorModel
) {

  var mode: Mode by mutableStateOf(Mode.NONE)

  enum class Mode {
    NONE,
    CROP,
    TEXT,
    DRAW,
    HIGHLIGHT,
    BLUR,
    MOVE_STICKER,
    MOVE_TEXT,
    DELETE,
    INSERT_STICKER
  }
}
