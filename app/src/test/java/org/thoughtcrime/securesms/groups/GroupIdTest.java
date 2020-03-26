package org.thoughtcrime.securesms.groups;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class GroupIdTest {

  @Test
  public void can_create_for_gv1() {
    GroupId groupId = GroupId.v1(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 });

    assertEquals("__textsecure_group__!0001020305060708090b0c0d0e0f", groupId.toString());
    assertFalse(groupId.isMmsGroup());
  }

  @Test
  public void can_parse_gv1() {
    GroupId groupId = GroupId.parse("__textsecure_group__!0001020305060708090b0c0d0e0f");

    assertEquals("__textsecure_group__!0001020305060708090b0c0d0e0f", groupId.toString());
    assertArrayEquals(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 }, groupId.getDecodedId());
    assertFalse(groupId.isMmsGroup());
  }

  @Test
  public void can_create_for_mms() {
    GroupId groupId = GroupId.mms(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 });

    assertEquals("__signal_mms_group__!0001020305060708090b0c0d0e0f", groupId.toString());
    assertTrue(groupId.isMmsGroup());
  }

  @Test
  public void can_parse_mms() {
    GroupId groupId = GroupId.parse("__signal_mms_group__!0001020305060708090b0c0d0e0f");

    assertEquals("__signal_mms_group__!0001020305060708090b0c0d0e0f", groupId.toString());
    assertArrayEquals(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 }, groupId.getDecodedId());
    assertTrue(groupId.isMmsGroup());
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void can_parse_null() {
    GroupId groupId = GroupId.parseNullable(null);

    assertNull(groupId);
  }
  
  @Test
  public void can_parse_gv1_with_parseNullable() {
    GroupId groupId = GroupId.parseNullable("__textsecure_group__!0001020305060708090b0c0d0e0f");

    assertEquals("__textsecure_group__!0001020305060708090b0c0d0e0f", groupId.toString());
    assertArrayEquals(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 }, groupId.getDecodedId());
    assertFalse(groupId.isMmsGroup());
  }
  
  @Test(expected = AssertionError.class)
  public void bad_encoding__bad_prefix__parseNullable() {
    GroupId.parseNullable("__BAD_PREFIX__!0001020305060708090b0c0d0e0f");
  }

  @Test(expected = AssertionError.class)
  public void bad_encoding__empty__parseNullable() {
    GroupId.parseNullable("");
  }
  
  @Test(expected = AssertionError.class)
  public void bad_encoding__odd_hex__parseNullable() {
    GroupId.parseNullable("__textsecure_group__!0001020305060708090bODD_HEX");
  }

  @Test(expected = AssertionError.class)
  public void bad_encoding__bad_prefix__parse() {
    GroupId.parse("__BAD_PREFIX__!0001020305060708090b0c0d0e0f");
  }
  
  @Test(expected = AssertionError.class)
  public void bad_encoding__odd_hex__parse() {
    GroupId.parse("__textsecure_group__!0001020305060708090b0c0d0e0fODD_HEX");
  }

  @Test
  public void get_bytes() {
    byte[] bytes = { 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 };
    GroupId groupId = GroupId.v1(bytes);

    assertArrayEquals(bytes, groupId.getDecodedId());
  }

  @Test
  public void equality() {
    GroupId groupId1 = GroupId.v1(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 });
    GroupId groupId2 = GroupId.v1(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 });

    assertNotSame(groupId1, groupId2);
    assertEquals(groupId1, groupId2);
    assertEquals(groupId1.hashCode(), groupId2.hashCode());
  }

  @Test
  public void inequality_by_bytes() {
    GroupId groupId1 = GroupId.v1(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 });
    GroupId groupId2 = GroupId.v1(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 16 });

    assertNotSame(groupId1, groupId2);
    assertNotEquals(groupId1, groupId2);
    assertNotEquals(groupId1.hashCode(), groupId2.hashCode());
  }

  @Test
  public void inequality_of_sms_and_mms() {
    GroupId groupId1 = GroupId.v1(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 });
    GroupId groupId2 = GroupId.mms(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 });

    assertNotSame(groupId1, groupId2);
    assertNotEquals(groupId1, groupId2);
    assertNotEquals(groupId1.hashCode(), groupId2.hashCode());
  }

  @Test
  public void inequality_with_null() {
    GroupId groupId = GroupId.v1(new byte[]{ 0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 });

    assertNotEquals(groupId, null);
  }
}
