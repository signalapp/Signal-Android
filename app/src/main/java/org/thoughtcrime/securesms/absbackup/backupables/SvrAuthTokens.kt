package org.thoughtcrime.securesms.absbackup.backupables

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.absbackup.AndroidBackupItem
import org.thoughtcrime.securesms.absbackup.protos.SvrAuthToken
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.io.IOException

/**
 * This backs up the not-secret KBS Auth tokens, which can be combined with a PIN to prove ownership of a phone number in order to complete the registration process.
 */
object SvrAuthTokens : AndroidBackupItem {
  private const val TAG = "KbsAuthTokens"

  override fun getKey(): String {
    return TAG
  }

  override fun getDataForBackup(): ByteArray {
    val proto = SvrAuthToken(svr2Tokens = SignalStore.svr.svr2AuthTokens)
    return proto.encode()
  }

  override fun restoreData(data: ByteArray) {
    if (SignalStore.svr.svr2AuthTokens.isNotEmpty()) {
      return
    }

    try {
      val proto = SvrAuthToken.ADAPTER.decode(data)

      SignalStore.svr.putSvr2AuthTokens(proto.svr2Tokens)
    } catch (e: IOException) {
      Log.w(TAG, "Cannot restore KbsAuthToken from backup service.")
    }
  }
}
