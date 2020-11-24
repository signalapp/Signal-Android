package org.whispersystems.signalservice.loki.protocol.shelved.multidevice

interface DeviceLinkingSessionListener {

  fun requestUserAuthorization(deviceLink: DeviceLink) { }
  fun onDeviceLinkRequestAuthorized(deviceLink: DeviceLink) { }
}
