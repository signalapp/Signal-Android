package org.thoughtcrime.securesms.groups.v2;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.GroupInviteLink;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.util.Base64UrlSafe;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public final class GroupInviteLinkUrl {

  private static final String GROUP_URL_HOST   = "group.signal.org";
  private static final String GROUP_URL_PREFIX = "https://" + GROUP_URL_HOST + "/#";

  private final GroupMasterKey    groupMasterKey;
  private final GroupLinkPassword password;
  private final String            url;

  public static @Nullable GroupInviteLinkUrl fromUrl(@NonNull String urlString)
      throws InvalidGroupLinkException, UnknownGroupLinkVersionException
  {
    URL url;
    try {
      url = new URL(urlString);
    } catch (MalformedURLException e) {
      return null;
    }

    try {
      if (!GROUP_URL_HOST.equalsIgnoreCase(url.getHost())) {
        return null;
      }

      if (!"/".equals(url.getPath()) && url.getPath().length() > 0) {
        throw new InvalidGroupLinkException("No path was expected in url");
      }

      String encoding = url.getRef();

      if (encoding == null || encoding.length() == 0) {
        throw new InvalidGroupLinkException("No reference was in the url");
      }

      byte[]          bytes           = Base64UrlSafe.decodePaddingAgnostic(encoding);
      GroupInviteLink groupInviteLink = GroupInviteLink.parseFrom(bytes);

      //noinspection SwitchStatementWithTooFewBranches
      switch (groupInviteLink.getContentsCase()) {
        case V1CONTENTS: {
            GroupInviteLink.GroupInviteLinkContentsV1 groupInviteLinkContentsV1 = groupInviteLink.getV1Contents();
            GroupMasterKey                            groupMasterKey            = new GroupMasterKey(groupInviteLinkContentsV1.getGroupMasterKey().toByteArray());
            GroupLinkPassword                         password                  = GroupLinkPassword.fromBytes(groupInviteLinkContentsV1.getInviteLinkPassword().toByteArray());

            return new GroupInviteLinkUrl(groupMasterKey, password);
        }
        default: throw new UnknownGroupLinkVersionException("Url contains no known group link content");
      }
    } catch (GroupLinkPassword.InvalidLengthException | InvalidInputException | IOException e){
      throw new InvalidGroupLinkException(e);
    }
  }

  private GroupInviteLinkUrl(@NonNull GroupMasterKey groupMasterKey, @NonNull GroupLinkPassword password) {
    this.groupMasterKey = groupMasterKey;
    this.password       = password;
    this.url            = createUrl(groupMasterKey, password);
  }

  protected static @NonNull String createUrl(@NonNull GroupMasterKey groupMasterKey, @NonNull GroupLinkPassword password) {
    GroupInviteLink groupInviteLink = GroupInviteLink.newBuilder()
                                                     .setV1Contents(GroupInviteLink.GroupInviteLinkContentsV1.newBuilder()
                                                                                   .setGroupMasterKey(ByteString.copyFrom(groupMasterKey.serialize()))
                                                                                   .setInviteLinkPassword(ByteString.copyFrom(password.serialize())))
                                                     .build();

    String encoding = Base64UrlSafe.encodeBytesWithoutPadding(groupInviteLink.toByteArray());

    return GROUP_URL_PREFIX + encoding;
  }

  public @NonNull String getUrl() {
    return url;
  }

  public @NonNull GroupMasterKey getGroupMasterKey() {
    return groupMasterKey;
  }

  public @NonNull GroupLinkPassword getPassword() {
    return password;
  }

  public final static class InvalidGroupLinkException extends Exception {
    public InvalidGroupLinkException(String message) {
      super(message);
    }

    public InvalidGroupLinkException(Throwable cause) {
      super(cause);
    }
  }

  public final static class UnknownGroupLinkVersionException extends Exception {
    public UnknownGroupLinkVersionException(String message) {
      super(message);
    }
  }
}
