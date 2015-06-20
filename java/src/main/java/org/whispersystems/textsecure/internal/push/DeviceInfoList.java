package org.whispersystems.textsecure.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.textsecure.api.messages.multidevice.DeviceInfo;

import java.util.List;

public class DeviceInfoList {

  @JsonProperty
  private List<DeviceInfo> devices;

  public DeviceInfoList() {}

  public List<DeviceInfo> getDevices() {
    return devices;
  }
}
