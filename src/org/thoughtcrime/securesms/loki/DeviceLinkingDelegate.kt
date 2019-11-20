package org.thoughtcrime.securesms.loki

import org.whispersystems.signalservice.loki.api.PairingAuthorisation

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

                override fun setDeviceDisplayName(hexEncodedPublicKey: String, displayName: String) {
                    for (delegate in validDelegates) { delegate.setDeviceDisplayName(hexEncodedPublicKey, displayName) }
                }
            }
        }
    }

    fun handleDeviceLinkAuthorized(pairingAuthorisation: PairingAuthorisation) {}
    fun handleDeviceLinkingDialogDismissed() {}
    fun sendPairingAuthorizedMessage(pairingAuthorisation: PairingAuthorisation) {}
    fun setDeviceDisplayName(hexEncodedPublicKey: String, displayName: String) {}
}