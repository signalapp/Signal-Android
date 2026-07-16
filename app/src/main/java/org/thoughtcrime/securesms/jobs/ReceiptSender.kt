package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.NoSessionException
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.SendMessageResult
import java.io.IOException

/**
 * Shared send logic for the receipt jobs ([SendReadReceiptJob], [SendDeliveryReceiptJob], [SendViewedReceiptJob]) that
 * will repair on first failure and then try again.
 */
object ReceiptSender {

  private val TAG = Log.tag(ReceiptSender::class.java)

  /**
   * @return the result of the send, or `null` if the receipt was dropped because the session could not be repaired.
   */
  @JvmStatic
  @Throws(IOException::class, UntrustedIdentityException::class)
  fun sendWithSessionRepair(recipientId: RecipientId, operation: ReceiptSendOperation): SendMessageResult? {
    return try {
      operation.send()
    } catch (e: NoSessionException) {
      Log.w(TAG, "Failed to send receipt due to a missing session. Archiving sessions and retrying.", e)

      AppDependencies.protocolStore.aci().sessions().archiveSessions(recipientId)
      AppDependencies.protocolStore.pni().sessions().archiveSessions(recipientId)

      try {
        operation.send()
      } catch (retryError: NoSessionException) {
        Log.w(TAG, "Failed to send receipt even after archiving sessions. Dropping.", retryError)
        null
      }
    }
  }

  fun interface ReceiptSendOperation {
    @Throws(IOException::class, UntrustedIdentityException::class, NoSessionException::class)
    fun send(): SendMessageResult
  }
}
