package org.thoughtcrime.securesms.loki

interface DeviceLinkingDialogDelegate {

    fun handleDeviceLinkAuthorized() // TODO: Device link
    fun handleDeviceLinkingDialogDismissed()
}