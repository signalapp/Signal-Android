package org.session.libsignal.service.loki.protocol.shelved.multidevice

interface DeviceLinkingSessionListener {

  fun requestUserAuthorization(deviceLink: DeviceLink) { }
  fun onDeviceLinkRequestAuthorized(deviceLink: DeviceLink) { }
}
