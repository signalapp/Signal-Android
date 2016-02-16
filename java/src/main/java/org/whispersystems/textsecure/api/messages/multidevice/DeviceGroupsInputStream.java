package org.whispersystems.textsecure.api.messages.multidevice;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentStream;
import org.whispersystems.textsecure.internal.push.TextSecureProtos;
import org.whispersystems.textsecure.internal.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class DeviceGroupsInputStream extends ChunkedInputStream{

  public DeviceGroupsInputStream(InputStream in) {
    super(in);
  }

  public DeviceGroup read() throws IOException {
    long   detailsLength     = readRawVarint32();
    byte[] detailsSerialized = new byte[(int)detailsLength];
    Util.readFully(in, detailsSerialized);

    TextSecureProtos.GroupDetails details = TextSecureProtos.GroupDetails.parseFrom(detailsSerialized);

    if (!details.hasId()) {
      throw new IOException("ID missing on group record!");
    }

    byte[]                               id      = details.getId().toByteArray();
    Optional<String>                     name    = Optional.fromNullable(details.getName());
    List<String>                         members = details.getMembersList();
    Optional<TextSecureAttachmentStream> avatar  = Optional.absent();
    boolean                              active  = details.getActive();

    if (details.hasAvatar()) {
      long        avatarLength      = details.getAvatar().getLength();
      InputStream avatarStream      = new ChunkedInputStream.LimitedInputStream(in, avatarLength);
      String      avatarContentType = details.getAvatar().getContentType();

      avatar = Optional.of(new TextSecureAttachmentStream(avatarStream, avatarContentType, avatarLength, null));
    }

    return new DeviceGroup(id, name, members, avatar, active);
  }

}
