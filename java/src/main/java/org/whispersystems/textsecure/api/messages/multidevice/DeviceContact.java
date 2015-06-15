package org.whispersystems.textsecure.api.messages.multidevice;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentStream;

public class DeviceContact {

  private final String                               number;
  private final Optional<String>                     name;
  private final Optional<TextSecureAttachmentStream> avatar;

  public DeviceContact(String number, Optional<String> name, Optional<TextSecureAttachmentStream> avatar) {
    this.number = number;
    this.name   = name;
    this.avatar = avatar;
  }

  public Optional<TextSecureAttachmentStream> getAvatar() {
    return avatar;
  }

  public Optional<String> getName() {
    return name;
  }

  public String getNumber() {
    return number;
  }

}
