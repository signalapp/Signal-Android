package org.thoughtcrime.securesms.absbackup.backupables

import com.google.protobuf.InvalidProtocolBufferException
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.absbackup.AndroidBackupItem
import org.thoughtcrime.securesms.absbackup.ExternalBackupProtos
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * This backs up the not-secret KBS Auth tokens, which can be combined with a PIN to prove ownership of a phone number in order to complete the registration process.
 */
object KbsAuthTokens : AndroidBackupItem {
  private const val TAG = "KbsAuthTokens"

  override fun getKey(): String {
    return TAG
  }

  override fun getDataForBackup(): ByteArray {
    val registrationRecoveryTokenList = SignalStore.kbsValues().kbsAuthTokenList
    val proto = ExternalBackupProtos.KbsAuthToken.newBuilder()
      .addAllToken(registrationRecoveryTokenList)
      .build()
    return proto.toByteArray()
  }

  override fun restoreData(data: ByteArray) {
    if (SignalStore.kbsValues().kbsAuthTokenList.isNotEmpty()) {
      return
    }

    try {
      val proto = ExternalBackupProtos.KbsAuthToken.parseFrom(data)

      SignalStore.kbsValues().putAuthTokenList(proto.tokenList)
    } catch (e: InvalidProtocolBufferException) {
      Log.w(TAG, "Cannot restore KbsAuthToken from backup service.")
    }
  }
}
