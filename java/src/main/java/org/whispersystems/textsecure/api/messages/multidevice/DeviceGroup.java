package org.whispersystems.textsecure.api.messages.multidevice;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentStream;

import java.util.List;

public class DeviceGroup {

  private final byte[]                               id;
  private final Optional<String>                     name;
  private final List<String>                         members;
  private final Optional<TextSecureAttachmentStream> avatar;

  public DeviceGroup(byte[] id, Optional<String> name, List<String> members, Optional<TextSecureAttachmentStream> avatar) {
    this.id      = id;
    this.name    = name;
    this.members = members;
    this.avatar  = avatar;
  }

  public Optional<TextSecureAttachmentStream> getAvatar() {
    return avatar;
  }

  public Optional<String> getName() {
    return name;
  }

  public byte[] getId() {
    return id;
  }

  public List<String> getMembers() {
    return members;
  }

}
