package org.thoughtcrime.securesms.loki

interface DeviceLinkingDialogDelegate {
    fun handleDeviceLinkAuthorized() { }
    fun handleDeviceLinkingDialogDismissed() { }
}