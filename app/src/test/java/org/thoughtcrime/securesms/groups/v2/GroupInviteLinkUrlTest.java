package org.thoughtcrime.securesms.groups.v2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.signal.core.util.Hex;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class GroupInviteLinkUrlTest {

  private final GroupMasterKey    groupMasterKey;
  private final GroupLinkPassword password;
  private final String            expectedUrl;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(

      givenGroup().withMasterKey("a501335111fa04e3756c24b6eb87264e2dfb622e8e1d339179765410776c0488")
                  .andPassword("f08b7e22fb938c025e6c158b7d544956")
                  .expectUrl("https://signal.group/#CjQKIKUBM1ER-gTjdWwktuuHJk4t-2Iujh0zkXl2VBB3bASIEhDwi34i-5OMAl5sFYt9VElW"),

      givenGroup().withMasterKey("2ca23c04d7cf60fe04039ae76d1912202c2a463d345d9cd48cf27f260dd37f6f")
                  .andPassword("2734457c02ce51da71ad0b62f3c222f7")
                  .expectUrl("https://signal.group/#CjQKICyiPATXz2D-BAOa520ZEiAsKkY9NF2c1IzyfyYN039vEhAnNEV8As5R2nGtC2LzwiL3"),

      givenGroup().withMasterKey("00f7e0c2a71ab064cc3ced4c04f08d7b7ef4b84b2c2206f69833be6cfe34df80")
                  .andPassword("9bc324eec437cfda6ae5b8aefbf47ee8")
                  .expectUrl("https://signal.group/#CjQKIAD34MKnGrBkzDztTATwjXt-9LhLLCIG9pgzvmz-NN-AEhCbwyTuxDfP2mrluK779H7o"),

      givenGroup().withMasterKey("00f7e0c2a71ab064cc3ced4c04f08d7b7ef4b84b2c2206f69833be6cfe34df80")
                  .andPassword("9b")
                  .expectUrl("https://signal.group/#CiUKIAD34MKnGrBkzDztTATwjXt-9LhLLCIG9pgzvmz-NN-AEgGb"),

      givenGroup().withMasterKey("2ca23c04d7cf60fe04039ae76d1912202c2a463d345d9cd48cf27f260dd37f6f")
                  .andPassword("2734457c02ce51da71ad0b62f3c222f7")
                  .expectUrl("sgnl://signal.group/#CjQKICyiPATXz2D-BAOa520ZEiAsKkY9NF2c1IzyfyYN039vEhAnNEV8As5R2nGtC2LzwiL3")
    );
  }

  public GroupInviteLinkUrlTest(GroupMasterKey groupMasterKey, GroupLinkPassword password, String expectedUrl) {
    this.groupMasterKey = groupMasterKey;
    this.password       = password;
    this.expectedUrl    = expectedUrl;
  }

  @Test
  public void can_extract_group_master_key_from_url() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
    assertEquals(groupMasterKey, GroupInviteLinkUrl.fromUri(expectedUrl).getGroupMasterKey());
  }

  @Test
  public void can_extract_group_master_key_from_url_padded() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
    assertEquals(groupMasterKey, GroupInviteLinkUrl.fromUri(expectedUrl + "=").getGroupMasterKey());
  }

  @Test
  public void can_extract_password_from_url() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
    assertEquals(password, GroupInviteLinkUrl.fromUri(expectedUrl).getPassword());
  }

  @Test
  public void can_extract_password_from_url_padded() throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException {
    assertEquals(password, GroupInviteLinkUrl.fromUri(expectedUrl + "=").getPassword());
  }

  @Test
  public void can_reconstruct_url() {
    String urlToCompare = expectedUrl;

    if (urlToCompare.startsWith("sgnl")) {
      urlToCompare = urlToCompare.replace("sgnl", "https");
    }

    assertEquals(urlToCompare, GroupInviteLinkUrl.createUrl(groupMasterKey, password));
  }

  private static TestBuilder givenGroup() {
    return new TestBuilder();
  }

  private static class TestBuilder {
    private GroupMasterKey groupMasterKey;
    private byte[]         passwordBytes;

    public TestBuilder withMasterKey(String groupMasterKeyAsHex) {
      try {
        groupMasterKey = new GroupMasterKey(Hex.fromStringOrThrow(groupMasterKeyAsHex));
      } catch (InvalidInputException e) {
        throw new AssertionError(e);
      }
      return this;
    }

    public TestBuilder andPassword(String passwordAsHex) {
      passwordBytes = Hex.fromStringOrThrow(passwordAsHex);
      return this;
    }

    public Object[] expectUrl(String url) {
      return new Object[]{ groupMasterKey, GroupLinkPassword.fromBytes(passwordBytes), url };
    }
  }
}
