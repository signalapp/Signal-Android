package org.thoughtcrime.securesms.groups.v2;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Test;
import org.signal.storageservice.protos.groups.GroupInviteLink;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.util.Base64UrlSafe;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertNull;

public final class GroupInviteLinkUrl_InvalidGroupLinkException_Test {

  @Test
  public void empty_string() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
    assertNull(GroupInviteLinkUrl.fromUrl(""));
  }

  @Test
  public void not_a_url_string() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
    assertNull(GroupInviteLinkUrl.fromUrl("abc"));
  }

  @Test
  public void wrong_host() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
    assertNull(GroupInviteLinkUrl.fromUrl("https://x.signal.org/#CAESNAogpQEzURH6BON1bCS264cmTi37Yi6OHTOReXZUEHdsBIgSEPCLfiL7k4wCXmwVi31USVY"));
  }

  @Test
  public void has_path() {
    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUrl("https://signal.group/not_expected/#CAESNAogpQEzURH6BON1bCS264cmTi37Yi6OHTOReXZUEHdsBIgSEPCLfiL7k4wCXmwVi31USVY"))
                      .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                      .hasMessage("No path was expected in url");
  }

  @Test
  public void missing_ref() {
    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUrl("https://signal.group/"))
                       .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                       .hasMessage("No reference was in the url");
  }

  @Test
  public void empty_ref() {
    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUrl("https://signal.group/#"))
                      .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                      .hasMessage("No reference was in the url");
  }

  @Test
  public void bad_base64() {
    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUrl("https://signal.group/#CAESNAogpQEzURH6BON1bCS264cmTi37Yi6HTOReXZUEHdsBIgSEPCLfiL7k4wCX;mwVi31USVY"))
                      .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                      .hasCauseExactlyInstanceOf(IOException.class);
  }

  @Test
  public void bad_protobuf() {
    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUrl("https://signal.group/#CAESNAogpQEzURH6BON1bCS264cmTi37Yi6HTOReXZUEHdsBIgSEPCLfiL7k4wCXmwVi31USVY"))
                      .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                      .hasCauseExactlyInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  public void version_999_url() {
    String url = "https://signal.group/#uj4zCiDMSxlNUvF4bQ3z3fYzGyZTFbJ1xEqWbPE3uZSD8bjOrxIP8NxV-0GUz3jpxMLR1rN3";

    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUrl(url))
                      .isInstanceOf(GroupInviteLinkUrl.UnknownGroupLinkVersionException.class)
                      .hasMessage("Url contains no known group link content");
  }

  @Test
  public void bad_master_key_length() {
    byte[]            masterKeyBytes = Util.getSecretBytes(33);
    GroupLinkPassword password       = GroupLinkPassword.createNew();

    String encoding = createEncodedProtobuf(masterKeyBytes, password.serialize());

    String url = "https://signal.group/#" + encoding;

    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUrl(url))
                      .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                      .hasCauseExactlyInstanceOf(InvalidInputException.class);
  }

  private static String createEncodedProtobuf(@NonNull byte[] groupMasterKey,
                                              @NonNull byte[] passwordBytes)
  {
    return Base64UrlSafe.encodeBytesWithoutPadding(GroupInviteLink.newBuilder()
                                                 .setV1Contents(GroupInviteLink.GroupInviteLinkContentsV1.newBuilder()
                                                                               .setGroupMasterKey(ByteString.copyFrom(groupMasterKey))
                                                                               .setInviteLinkPassword(ByteString.copyFrom(passwordBytes)))
                                                 .build()
                                                 .toByteArray());
  }

}