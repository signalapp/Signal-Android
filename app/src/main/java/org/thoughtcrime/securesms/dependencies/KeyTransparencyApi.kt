package org.thoughtcrime.securesms.dependencies

import org.signal.libsignal.keytrans.KeyTransparencyException
import org.signal.libsignal.net.KeyTransparency
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.getOrError
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ServiceId
import org.thoughtcrime.securesms.database.model.KeyTransparencyStore
import org.whispersystems.signalservice.api.websocket.SignalWebSocket

/**
 * Operations used when interacting with [org.signal.libsignal.net.KeyTransparencyClient]
 */
class KeyTransparencyApi(private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket) {

  /**
   * Uses KT to verify recipient. This is an unauthenticated and should only be called the first time KT is being requested for this recipient.
   */
  suspend fun search(aci: ServiceId.Aci, aciIdentityKey: IdentityKey, e164: String?, unidentifiedAccessKey: ByteArray?, usernameHash: ByteArray?, keyTransparencyStore: KeyTransparencyStore): RequestResult<Unit, KeyTransparencyException> {
    return unauthWebSocket.runCatchingWithUnauthChatConnection { chatConnection ->
      chatConnection.keyTransparencyClient().search(aci, aciIdentityKey, e164, unidentifiedAccessKey, usernameHash, keyTransparencyStore)
    }.getOrError()
  }

  /**
   * Monitors KT to verify recipient. This is an unauthenticated and should only be called following a successful [search].
   */
  suspend fun monitor(monitorMode: KeyTransparency.MonitorMode, aci: ServiceId.Aci, aciIdentityKey: IdentityKey, e164: String?, unidentifiedAccessKey: ByteArray?, usernameHash: ByteArray?, keyTransparencyStore: KeyTransparencyStore): RequestResult<Unit, KeyTransparencyException> {
    return unauthWebSocket.runCatchingWithUnauthChatConnection { chatConnection ->
      chatConnection.keyTransparencyClient().monitor(monitorMode, aci, aciIdentityKey, e164, unidentifiedAccessKey, usernameHash, keyTransparencyStore)
    }.getOrError()
  }
}
