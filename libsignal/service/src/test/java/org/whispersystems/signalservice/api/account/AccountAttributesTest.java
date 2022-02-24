package org.whispersystems.signalservice.api.account;

import org.junit.Test;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import static org.junit.Assert.assertEquals;

public final class AccountAttributesTest {

  @Test
  public void can_write_account_attributes() {
    String json = JsonUtil.toJson(new AccountAttributes("skey",
                                                        123,
                                                        true,
                                                        "1234",
                                                        "reglock1234",
                                                        new byte[10],
                                                        false,
                                                        new AccountAttributes.Capabilities(true, true, true, true, true, true, true, true),
                                                        false,
                                                        null));
    assertEquals("{\"signalingKey\":\"skey\"," +
                 "\"registrationId\":123," +
                 "\"voice\":true," +
                 "\"video\":true," +
                 "\"fetchesMessages\":true," +
                 "\"pin\":\"1234\"," +
                 "\"registrationLock\":\"reglock1234\"," +
                 "\"unidentifiedAccessKey\":\"AAAAAAAAAAAAAA==\"," +
                 "\"unrestrictedUnidentifiedAccess\":false," +
                 "\"discoverableByPhoneNumber\":false," +
                 "\"capabilities\":{\"uuid\":true,\"storage\":true,\"senderKey\":true,\"announcementGroup\":true,\"changeNumber\":true,\"stories\":true,\"gv2-3\":true,\"gv1-migration\":true}," +
                 "\"name\":null}", json);
  }

  @Test
  public void gv2_true() {
    String json = JsonUtil.toJson(new AccountAttributes.Capabilities(false, true, false, false, false, false, false, false));
    assertEquals("{\"uuid\":false,\"storage\":false,\"senderKey\":false,\"announcementGroup\":false,\"changeNumber\":false,\"stories\":false,\"gv2-3\":true,\"gv1-migration\":false}", json);
  }

  @Test
  public void gv2_false() {
    String json = JsonUtil.toJson(new AccountAttributes.Capabilities(false, false, false, false, false, false, false, false));
    assertEquals("{\"uuid\":false,\"storage\":false,\"senderKey\":false,\"announcementGroup\":false,\"changeNumber\":false,\"stories\":false,\"gv2-3\":false,\"gv1-migration\":false}", json);
  }
}