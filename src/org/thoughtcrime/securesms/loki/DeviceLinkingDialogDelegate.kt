package org.thoughtcrime.securesms.loki

import org.whispersystems.signalservice.loki.api.LokiPairingAuthorisation

interface DeviceLinkingDialogDelegate {
    fun handleDeviceLinkAuthorized() {}
    fun handleDeviceLinkingDialogDismissed() {}
}

interface DeviceLinkingViewDelegate: DeviceLinkingDialogDelegate {
    fun authorise(pairing: LokiPairingAuthorisation): Boolean { return false }
}