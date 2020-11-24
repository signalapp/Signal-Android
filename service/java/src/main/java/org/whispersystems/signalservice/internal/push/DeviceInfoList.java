package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;

import java.util.List;

public class DeviceInfoList {

  @JsonProperty
  private List<DeviceInfo> devices;

  public DeviceInfoList() {}

  public List<DeviceInfo> getDevices() {
    return devices;
  }
}
