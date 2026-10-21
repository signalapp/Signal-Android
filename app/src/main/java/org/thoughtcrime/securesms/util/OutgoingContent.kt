package org.thoughtcrime.securesms.util

import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.core.util.UuidUtil
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.groups.GroupSessionBuilder
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SignalGroupSessionBuilder
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.NullMessage
import org.whispersystems.signalservice.internal.push.PniSignatureMessage
import org.whispersystems.signalservice.internal.push.ReceiptMessage
import org.whispersystems.signalservice.internal.util.Util
import java.util.Optional

object OutgoingContent {

  private val TAG = Log.tag(OutgoingContent::class.java)

  private const val MAX_NULL_MESSAGE_PADDING_BYTES = 140

  fun nullMessage(): EnvelopeContent {
    val padding = DataMessage(body = Base64.encodeWithPadding(Util.getRandomLengthSecretBytes(MAX_NULL_MESSAGE_PADDING_BYTES))).encode()
    val content = Content(nullMessage = NullMessage(padding = padding.toByteString()))
    return EnvelopeContent.encrypted(content, ContentHint.IMPLICIT, Optional.empty())
  }

  fun receipt(type: ReceiptMessage.Type, timestamps: List<Long>, includePniSignature: Boolean): EnvelopeContent {
    val content = Content(
      receiptMessage = ReceiptMessage(type = type, timestamp = timestamps),
      pniSignatureMessage = if (includePniSignature) pniSignature() else null
    )
    return EnvelopeContent.encrypted(content, ContentHint.IMPLICIT, Optional.empty())
  }

  fun pniSignature(): PniSignatureMessage? {
    val pni = SignalStore.account.pni
    val pniIdentity = SignalStore.account.pniIdentityKeyOrNull

    if (pni == null || pniIdentity == null) {
      Log.w(TAG, "Tried to create a PNI signature message, but we have no PNI! Skipping.")
      return null
    }

    val signature = pniIdentity.signAlternateIdentity(SignalStore.account.aciIdentityKey.publicKey)

    return PniSignatureMessage(
      pni = UuidUtil.toByteString(pni.rawUuid),
      signature = signature.toByteString()
    )
  }

  /**
   * Returns our sender key distribution message for [distributionId], creating a sender key session if one doesn't exist.
   */
  fun senderKeyDistributionMessage(distributionId: DistributionId): SenderKeyDistributionMessage {
    val self = SignalStore.account.requireAci().toProtocolAddress(SignalStore.account.deviceId)
    return SignalGroupSessionBuilder(ReentrantSessionLock.INSTANCE, GroupSessionBuilder(AppDependencies.protocolStore.aci())).create(self, distributionId.asUuid())
  }
}
