package org.thoughtcrime.securesms.database;


import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class AddressTest {

  @Before
  public void setup() {}

  @Test
  public void testAddressString() throws Exception {
    Address.ExternalAddressFormatter formatter = new Address.ExternalAddressFormatter("+14152222222");
    assertEquals(formatter.format("bonbon"), "bonbon");
  }

  @Test
  public void testAddressShortCode() throws Exception {
    Address.ExternalAddressFormatter formatter = new Address.ExternalAddressFormatter("+14152222222");
    assertEquals(formatter.format("40404"), "40404");
  }

  @Test
  public void testEmailAddress() throws Exception {
    Address.ExternalAddressFormatter formatter = new Address.ExternalAddressFormatter("+14152222222");
    assertEquals(formatter.format("junk@junk.net"), "junk@junk.net");
  }

  @Test
  public void testNumberArbitrary() throws Exception {
    Address.ExternalAddressFormatter formatter = new Address.ExternalAddressFormatter("+14152222222");
    assertEquals(formatter.format("(415) 111-1122"), "+14151111122");
    assertEquals(formatter.format("(415) 111 1123"), "+14151111123");
    assertEquals(formatter.format("415-111-1124"), "+14151111124");
    assertEquals(formatter.format("415.111.1125"), "+14151111125");
    assertEquals(formatter.format("+1 415.111.1126"), "+14151111126");
    assertEquals(formatter.format("+1 415 111 1127"), "+14151111127");
    assertEquals(formatter.format("+1 (415) 111 1128"), "+14151111128");

    formatter = new Address.ExternalAddressFormatter("+442079460010");
    assertEquals(formatter.format("(020) 7946 0018"), "+442079460018");
  }

  @Test
  public void testGroup() throws Exception {
    Address.ExternalAddressFormatter formatter = new Address.ExternalAddressFormatter("+14152222222");
    assertEquals(formatter.format("__textsecure_group__!foobar"), "__textsecure_group__!foobar");
  }

  @Test
  public void testLostLocalNumber() throws Exception {
    Address.ExternalAddressFormatter formatter = new Address.ExternalAddressFormatter("US", true);
    assertEquals(formatter.format("(415) 111-1122"), "+14151111122");
  }

}
