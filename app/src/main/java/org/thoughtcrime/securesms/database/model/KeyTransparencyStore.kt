package org.thoughtcrime.securesms.database.model

import org.signal.core.util.logging.Log.tag
import org.signal.libsignal.keytrans.Store
import org.signal.libsignal.protocol.ServiceId
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.util.Optional

/**
 * Store used by [org.signal.libsignal.net.KeyTransparencyClient] during key transparency
 */
data object KeyTransparencyStore : Store {

  private val TAG: String = tag(KeyTransparencyStore::class.java)

  override fun getLastDistinguishedTreeHead(): Optional<ByteArray> {
    return Optional.ofNullable(SignalStore.account.distinguishedHead)
  }

  override fun setLastDistinguishedTreeHead(lastDistinguishedTreeHead: ByteArray) {
    SignalStore.account.distinguishedHead = lastDistinguishedTreeHead
  }

  override fun getAccountData(libsignalAci: ServiceId.Aci): Optional<ByteArray> {
    val aci = org.signal.core.models.ServiceId.ACI.fromLibSignal(libsignalAci)
    return Optional.ofNullable(SignalDatabase.recipients.getKeyTransparencyData(aci))
  }

  override fun setAccountData(libsignalAci: ServiceId.Aci, data: ByteArray) {
    val aci = org.signal.core.models.ServiceId.ACI.fromLibSignal(libsignalAci)
    SignalDatabase.recipients.setKeyTransparencyData(aci, data)
  }
}
