package org.thoughtcrime.securesms.loki

import org.whispersystems.signalservice.loki.api.PairingAuthorisation

interface DeviceLinkingViewDelegate {
    fun handleDeviceLinkAuthorized() { }
    fun handleDeviceLinkingDialogDismissed() { }
    fun sendPairingAuthorizedMessage(pairing: PairingAuthorisation): Boolean { return false }
}