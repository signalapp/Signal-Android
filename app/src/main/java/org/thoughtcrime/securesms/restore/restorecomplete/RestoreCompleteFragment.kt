/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.restorecomplete

import android.os.Bundle
import android.view.View
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.restore.RestoreActivity

/**
 * This is a hack placeholder fragment so we can reuse the existing V1 device transfer fragments without changing their navigation calls.
 * The original calls expect to be navigating from the [NewDeviceTransferCompleteFragment] to [EnterPhoneNumberFragment]
 * This approximates that by taking the place of [EnterPhoneNumberFragment],
 * then bridging us back to [RegistrationV2Activity] by immediately closing the [RestoreActivity].
 */
class RestoreCompleteFragment : LoggingFragment(R.layout.fragment_registration_blank) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    Log.d(TAG, "Finishing activity…")
    onBackupCompletedSuccessfully()
  }

  private fun onBackupCompletedSuccessfully() {
    Log.d(TAG, "onBackupCompletedSuccessfully()")
    val activity = requireActivity() as RestoreActivity
    activity.finishActivitySuccessfully()
  }

  companion object {
    private val TAG = Log.tag(RestoreCompleteFragment::class.java)
  }
}
