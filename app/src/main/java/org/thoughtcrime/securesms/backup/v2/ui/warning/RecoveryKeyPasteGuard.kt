/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.warning

import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.components.ComposeText
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Wires this [ComposeText] so that pasting the user's own recovery key first shows
 * [RecoveryKeyPasteWarningFragment], warning against sharing it. The paste only completes if the
 * user explicitly confirms via that warning.
 *
 * Must be called once the [host]'s view has been created, as it registers a fragment result
 * listener scoped to the host's view lifecycle.
 *
 * @param onWarningShown invoked just before the warning is shown. Hosts that auto-dismiss when the
 * keyboard hides (e.g. [org.thoughtcrime.securesms.components.KeyboardEntryDialogFragment]) can use
 * this to suppress that behavior while the warning is up.
 * @param onWarningDismissed invoked when the warning is dismissed by any path, after the paste (if
 * any) has been applied. Hosts can use this to restore the suppressed state and re-focus the input.
 */
fun ComposeText.guardAgainstRecoveryKeyPaste(
  host: Fragment,
  onWarningShown: () -> Unit = {},
  onWarningDismissed: () -> Unit = {}
) {
  var pendingPaste: CharSequence? = null

  host.childFragmentManager.setFragmentResultListener(RecoveryKeyPasteWarningFragment.REQUEST_KEY, host.viewLifecycleOwner) { _, bundle ->
    if (bundle.getBoolean(RecoveryKeyPasteWarningFragment.REQUEST_KEY)) {
      pendingPaste?.let { insertText(it) }
    }
    pendingPaste = null
    onWarningDismissed()
  }

  setOnPasteListener { pasteText ->
    if (RecoveryKeyDetector.containsRecoveryKey(pasteText?.toString(), SignalStore.account.accountEntropyPoolOrNull)) {
      pendingPaste = pasteText
      onWarningShown()
      RecoveryKeyPasteWarningFragment().show(host.childFragmentManager, null)
      true
    } else {
      false
    }
  }
}
