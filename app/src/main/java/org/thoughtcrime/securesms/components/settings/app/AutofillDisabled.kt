/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app

import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps password managers from offering to save what's typed on the current screen, as a [org.signal.core.ui.compose.ComposeFragment]
 * with autofill disabled does, for screens hosted as Nav3 entries instead.
 */
@Composable
fun AutofillDisabled() {
  if (Build.VERSION.SDK_INT < 26) {
    return
  }

  val view = LocalView.current
  DisposableEffect(view) {
    val previous = view.importantForAutofill
    view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    onDispose { view.importantForAutofill = previous }
  }
}
