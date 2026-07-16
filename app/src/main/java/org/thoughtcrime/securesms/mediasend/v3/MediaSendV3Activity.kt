/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.mediasend.HudCommand
import org.signal.mediasend.MediaSendActivityContract
import org.signal.mediasend.MediaSendScreen
import org.signal.mediasend.edit.LocalAddAMessageRowTextField
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.mediasend.v2.review.AddMessageDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.registration.olddevice.QuickTransferOldDeviceActivity
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Encapsulates the media send flow for v3.
 */
class MediaSendV3Activity : PassphraseRequiredActivity() {

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableEdgeToEdge()

    val contractArgs = MediaSendActivityContract.Args.fromIntent(intent)

    setContent {
      CompositionLocalProvider(
        LocalAddAMessageRowTextField provides { message, modifier ->
          AndroidView(
            factory = { EmojiTextView(it) },
            update = { view ->
              view.text = message
            },
            modifier = modifier
          )
        }
      ) {
        MediaSendScreen(
          contractArgs = contractArgs,
          sendSlot = {
            AndroidFragment(
              clazz = MediaSendV3ForwardFragment::class.java,
              modifier = Modifier.fillMaxSize()
            )
          },
          onExternalHudCommand = {
            when (it) {
              is HudCommand.ShowAddAMessageDialog -> {
                AddMessageDialogFragment.show(
                  fragmentManager = supportFragmentManager,
                  addAMessageDialog = it,
                  destination = contractArgs.recipientId?.let {
                    RecipientId.from(it.id)
                  }
                )
              }

              is HudCommand.GoToConversation -> {
                lifecycleScope.launch(Dispatchers.Default) {
                  val recipient = Recipient.resolved(RecipientId.from(it.recipientId.id))
                  withContext(Dispatchers.Main) {
                    CommunicationActions.startConversation(
                      this@MediaSendV3Activity,
                      recipient,
                      null
                    )
                  }
                }
              }

              HudCommand.GoToLinkedDevices -> {
                startActivity(AppSettingsActivity.linkedDevices(this))
                finish()
              }

              is HudCommand.GoToQuickTransfer -> {
                startActivity(QuickTransferOldDeviceActivity.intent(this, it.qrData))
                finish()
              }
            }
          }
        )
      }
    }
  }
}
