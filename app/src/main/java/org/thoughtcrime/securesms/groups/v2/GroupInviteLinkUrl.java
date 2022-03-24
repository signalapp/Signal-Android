package org.thoughtcrime.securesms.groups.v2;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.GroupInviteLink;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.util.Base64UrlSafe;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public final class GroupInviteLinkUrl {

  private static final String GROUP_URL_HOST   = "signal.group";
  private static final String GROUP_URL_PREFIX = "https://" + GROUP_URL_HOST + "/#";

  private final GroupMasterKey    groupMasterKey;
  private final GroupLinkPassword password;
  private final String            url;

  public static GroupInviteLinkUrl forGroup(@NonNull GroupMasterKey groupMasterKey,
                                            @NonNull DecryptedGroup group)
  {
    return new GroupInviteLinkUrl(groupMasterKey, GroupLinkPassword.fromBytes(group.getInviteLinkPassword().toByteArray()));
  }

  public static boolean isGroupLink(@NonNull String urlString) {
    return getGroupUrl(urlString) != null;
  }

  /**
   * @return null iff not a group url.
   * @throws InvalidGroupLinkException If group url, but cannot be parsed.
   */
  public static @Nullable GroupInviteLinkUrl fromUri(@NonNull String urlString)
      throws InvalidGroupLinkException, UnknownGroupLinkVersionException
  {
    URI uri = getGroupUrl(urlString);

    if (uri == null) {
      return null;
    }

    try {
      if (!"/".equals(uri.getPath()) && uri.getPath().length() > 0) {
        throw new InvalidGroupLinkException("No path was expected in uri");
      }

      String encoding = uri.getFragment();

      if (encoding == null || encoding.length() == 0) {
        throw new InvalidGroupLinkException("No reference was in the uri");
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
    } catch (InvalidInputException | IOException e) {
      throw new InvalidGroupLinkException(e);
    }
  }

  /**
   * @return {@link URI} if the host name matches.
   */
  private static URI getGroupUrl(@NonNull String urlString) {
    try {
      URI url = new URI(urlString);

      if (!"https".equalsIgnoreCase(url.getScheme()) &&
          !"sgnl".equalsIgnoreCase(url.getScheme()))
      {
        return null;
      }

      return GROUP_URL_HOST.equalsIgnoreCase(url.getHost())
             ? url
             : null;

    } catch (URISyntaxException e) {
      return null;
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
