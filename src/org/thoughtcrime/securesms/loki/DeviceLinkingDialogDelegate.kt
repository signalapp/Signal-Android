package org.thoughtcrime.securesms.loki

import org.whispersystems.signalservice.loki.api.PairingAuthorisation

interface DeviceLinkingDialogDelegate {

    fun handleDeviceLinkAuthorized(pairingAuthorisation: PairingAuthorisation) { }
    fun handleDeviceLinkingDialogDismissed() { }
    fun sendPairingAuthorizedMessage(pairingAuthorisation: PairingAuthorisation) { }
}