package org.thoughtcrime.securesms.loki

import org.whispersystems.signalservice.loki.api.PairingAuthorisation

interface DeviceLinkingDialogDelegate {

    fun handleDeviceLinkAuthorized(pairing: PairingAuthorisation) { }
    fun handleDeviceLinkingDialogDismissed() { }
}