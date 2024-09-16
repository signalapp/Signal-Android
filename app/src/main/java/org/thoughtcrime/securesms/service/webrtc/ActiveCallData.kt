/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.bundleOf
import kotlinx.parcelize.Parcelize
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState

/**
 * Active call data to be returned from calls to isInCallQuery.
 */
@Parcelize
data class ActiveCallData(
  val recipientId: RecipientId
) : Parcelable {

  companion object {
    private const val KEY = "ACTIVE_CALL_DATA"

    @JvmStatic
    fun fromCallState(webRtcServiceState: WebRtcServiceState): ActiveCallData {
      return ActiveCallData(
        webRtcServiceState.callInfoState.callRecipient.id
      )
    }

    @JvmStatic
    fun fromBundle(bundle: Bundle): ActiveCallData {
      return bundle.getParcelableCompat(KEY, ActiveCallData::class.java)!!
    }
  }

  fun toBundle(): Bundle = bundleOf(KEY to this)
}
