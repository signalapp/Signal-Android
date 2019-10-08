package org.thoughtcrime.securesms.loki

import org.whispersystems.signalservice.loki.api.PairingAuthorisation

interface DeviceLinkingViewDelegate {

    fun handleDeviceLinkAuthorized(pairing: PairingAuthorisation) { }
    fun handleDeviceLinkingDialogDismissed() { }
    fun sendPairingAuthorizedMessage(pairing: PairingAuthorisation): Boolean { return false }
}