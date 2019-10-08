package org.thoughtcrime.securesms.loki

import org.whispersystems.signalservice.loki.api.PairingAuthorisation

interface DeviceLinkingViewDelegate {

    fun handleDeviceLinkAuthorized(pairingAuthorisation: PairingAuthorisation) { }
    fun handleDeviceLinkingDialogDismissed() { }
    fun sendPairingAuthorizedMessage(pairingAuthorisation: PairingAuthorisation) { }
}