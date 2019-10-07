package org.thoughtcrime.securesms.loki

import org.whispersystems.signalservice.loki.api.PairingAuthorisation

interface DeviceLinkingDialogDelegate {
    fun handleDeviceLinkAuthorized() {}
    fun handleDeviceLinkingDialogDismissed() {}
}

interface DeviceLinkingViewDelegate: DeviceLinkingDialogDelegate {
    fun authorise(pairing: PairingAuthorisation): Boolean { return false }
}