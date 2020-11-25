package org.session.libsignal.service.loki.protocol.shelved.multidevice

class DeviceLinkingSession {
    private val listeners = mutableListOf<DeviceLinkingSessionListener>()
    var isListeningForLinkingRequests: Boolean = false
        private set

    companion object {
        val shared = DeviceLinkingSession()
    }

    fun addListener(listener: DeviceLinkingSessionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DeviceLinkingSessionListener) {
        listeners.remove(listener)
    }

    fun startListeningForLinkingRequests() {
        isListeningForLinkingRequests = true
    }

    fun stopListeningForLinkingRequests() {
        isListeningForLinkingRequests = false
    }

    fun processLinkingRequest(deviceLink: DeviceLink) {
        if (!isListeningForLinkingRequests || !deviceLink.verify()) { return }
        listeners.forEach { it.requestUserAuthorization(deviceLink) }
    }

    fun processLinkingAuthorization(deviceLink: DeviceLink) {
        if (!isListeningForLinkingRequests || !deviceLink.verify()) { return }
        listeners.forEach { it.onDeviceLinkRequestAuthorized(deviceLink) }
    }
}
