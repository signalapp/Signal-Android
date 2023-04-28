package org.thoughtcrime.securesms.groups.v2;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Test;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.storageservice.protos.groups.GroupInviteLink;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.util.Base64UrlSafe;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertNull;

public final class GroupInviteLinkUrl_InvalidGroupLinkException_Test {

  @Test
  public void empty_string() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
    assertNull(GroupInviteLinkUrl.fromUri(""));
  }

  @Test
  public void not_a_url_string() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
    assertNull(GroupInviteLinkUrl.fromUri("abc"));
  }

  @Test
  public void wrong_host() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
    assertNull(GroupInviteLinkUrl.fromUri("https://x.signal.org/#CjQKIAD34MKnGrBkzDztTATwjXt-9LhLLCIG9pgzvmz-NN-AEhCbwyTuxDfP2mrluK779H7o"));
  }

  @Test
  public void wrong_scheme() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
    assertNull(GroupInviteLinkUrl.fromUri("http://signal.group/#CjQKIAD34MKnGrBkzDztTATwjXt-9LhLLCIG9pgzvmz-NN-AEhCbwyTuxDfP2mrluK779H7o"));
  }

  @Test
  public void has_path() {
    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri("https://signal.group/not_expected/#CjQKIAD34MKnGrBkzDztTATwjXt-9LhLLCIG9pgzvmz-NN-AEhCbwyTuxDfP2mrluK779H7o"))
                      .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                      .hasMessage("No path was expected in uri");
  }

  @Test
  public void missing_ref() {
    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri("https://signal.group/"))
                       .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                       .hasMessage("No reference was in the uri");
  }

  @Test
  public void empty_ref() {
    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri("https://signal.group/#"))
                      .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                      .hasMessage("No reference was in the uri");
  }

  @Test
  public void bad_base64() {
    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri("https://signal.group/#CAESNAogpQEzURH6BON1bCS264cmTi37Yi6HTOReXZUEHdsBIgSEPCLfiL7k4wCX;mwVi31USVY"))
                      .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                      .hasCauseExactlyInstanceOf(IOException.class);
  }

  @Test
  public void bad_protobuf() {
    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri("https://signal.group/#CAESNAogpQEzURH6BON1bCS264cmTi37Yi6HTOReXZUEHdsBIgSEPCLfiL7k4wCXmwVi31USVY"))
                      .isInstanceOf(GroupInviteLinkUrl.InvalidGroupLinkException.class)
                      .hasCauseExactlyInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  public void version_999_url() {
    String url = "https://signal.group/#uj4zCiDMSxlNUvF4bQ3z3fYzGyZTFbJ1xEqWbPE3uZSD8bjOrxIP8NxV-0GUz3jpxMLR1rN3";

    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri(url))
                      .isInstanceOf(GroupInviteLinkUrl.UnknownGroupLinkVersionException.class)
                      .hasMessage("Url contains no known group link content");
  }

  @Test
  public void bad_master_key_length() {
    byte[]            masterKeyBytes = Util.getSecretBytes(33);
    GroupLinkPassword password       = GroupLinkPassword.createNew();

    String encoding = createEncodedProtobuf(masterKeyBytes, password.serialize());

    String url = "https://signal.group/#" + encoding;

    assertThatThrownBy(() -> GroupInviteLinkUrl.fromUri(url))
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
