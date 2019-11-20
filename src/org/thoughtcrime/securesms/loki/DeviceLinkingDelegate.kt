package org.thoughtcrime.securesms.loki

import org.whispersystems.signalservice.loki.api.PairingAuthorisation

// Loki - TODO: Remove this yucky delegate pattern for device linking dialog once we have the redesign
interface DeviceLinkingDelegate {
    companion object {
        fun combine(vararg delegates: DeviceLinkingDelegate?): DeviceLinkingDelegate {
            val validDelegates = delegates.filterNotNull()
            return object : DeviceLinkingDelegate {
                override fun handleDeviceLinkAuthorized(pairingAuthorisation: PairingAuthorisation) {
                    for (delegate in validDelegates) { delegate.handleDeviceLinkAuthorized(pairingAuthorisation) }
                }

                override fun handleDeviceLinkingDialogDismissed() {
                    for (delegate in validDelegates) { delegate.handleDeviceLinkingDialogDismissed() }
                }

                override fun sendPairingAuthorizedMessage(pairingAuthorisation: PairingAuthorisation) {
                    for (delegate in validDelegates) { delegate.sendPairingAuthorizedMessage(pairingAuthorisation) }
                }
            }
        }
    }

    fun handleDeviceLinkAuthorized(pairingAuthorisation: PairingAuthorisation) {}
    fun handleDeviceLinkingDialogDismissed() {}
    fun sendPairingAuthorizedMessage(pairingAuthorisation: PairingAuthorisation) {}
}