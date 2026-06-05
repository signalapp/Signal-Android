/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import org.signal.mediasend.MediaSendActivityContract
import org.signal.mediasend.MediaSendScreen
import org.thoughtcrime.securesms.PassphraseRequiredActivity

/**
 * Encapsulates the media send flow for v3.
 */
class MediaSendV3Activity : PassphraseRequiredActivity() {

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableEdgeToEdge()

    val contractArgs = MediaSendActivityContract.Args.fromIntent(intent)

    setContent {
      MediaSendScreen(
        contractArgs = contractArgs,
        sendSlot = {
          AndroidFragment(
            clazz = MediaSendV3ForwardFragment::class.java,
            modifier = Modifier.fillMaxSize()
          )
        }
      )
    }
  }
}
