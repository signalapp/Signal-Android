/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.olddevice.transferaccount

sealed interface TransferScreenEvents {
  data object TransferClicked : TransferScreenEvents
  data object ContinueOnOtherDeviceDismiss : TransferScreenEvents
  data object ErrorDialogDismissed : TransferScreenEvents
  data object NavigateBack : TransferScreenEvents
}
