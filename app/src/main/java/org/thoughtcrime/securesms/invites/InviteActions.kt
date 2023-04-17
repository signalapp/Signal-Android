package org.thoughtcrime.securesms.invites

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.MainThread
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.Util

/**
 * Handles 'invite to signal' actions.
 */
object InviteActions {
  /**
   * Called to send a message to a user to invite them to Signal.
   * The invite can be sent in one of three ways:
   *
   * 1. If Signal is the user's default SMS app, we can simply append the message to the composer.
   * 2. If the user has an sms address, we generate a SENDTO intent and request it to be launched.
   * 3. Otherwise, we generate a share intent to allow the user to select how to send the invite.
   */
  @MainThread
  fun inviteUserToSignal(
    context: Context,
    recipient: Recipient,
    appendInviteToComposer: ((String) -> Unit)?,
    launchIntent: (Intent) -> Unit
  ) {
    val inviteText = context.getString(
      R.string.ConversationActivity_lets_switch_to_signal,
      context.getString(R.string.install_url)
    )

    if (appendInviteToComposer != null && Util.isDefaultSmsProvider(context) && SignalStore.misc().smsExportPhase.isSmsSupported()) {
      appendInviteToComposer(inviteText)
    } else if (recipient.hasSmsAddress()) {
      launchIntent(
        CommunicationActions.createIntentToComposeSmsThroughDefaultApp(recipient, inviteText)
      )
    } else {
      val intent = CommunicationActions.createIntentToShareTextViaShareSheet(inviteText)

      if (intent.resolveActivity(context.packageManager) != null) {
        launchIntent(Intent.createChooser(intent, context.getString(R.string.InviteActivity_invite_to_signal)))
      } else {
        Toast.makeText(context, R.string.InviteActivity_no_app_to_share_to, Toast.LENGTH_LONG).show()
      }
    }
  }
}
