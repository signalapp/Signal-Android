package org.thoughtcrime.securesms.recipients;

import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public final class RecipientIdSerializationTest {

  @Test
  public void toSerializedList_empty() {
    assertEquals("", RecipientId.toSerializedList(emptyList()));
  }

  @Test
  public void toSerializedList_one_item() {
    assertEquals("123", RecipientId.toSerializedList(singletonList(RecipientId.from(123))));
  }

  @Test
  public void toSerializedList_two_items() {
    assertEquals("123,987", RecipientId.toSerializedList(asList(RecipientId.from(123), RecipientId.from("987"))));
  }

  @Test
  public void fromSerializedList_empty() {
    assertThat(RecipientId.fromSerializedList(""), is(emptyList()));
  }

  @Test
  public void fromSerializedList_one_item() {
    assertThat(RecipientId.fromSerializedList("123"), is(singletonList(RecipientId.from(123))));
  }

  @Test
  public void fromSerializedList_two_items() {
    assertThat(RecipientId.fromSerializedList("123,456"), is(asList(RecipientId.from(123), RecipientId.from(456))));
  }

  @Test
  public void fromSerializedList_recipient_serialize() {
    List<RecipientId> recipientIds = RecipientId.fromSerializedList(RecipientId.from(123).serialize());
    assertThat(recipientIds, hasSize(1));
    assertThat(recipientIds, contains(RecipientId.from(123)));
  }

  @Test
  public void serializedListContains_empty_list_does_not_contain_item() {
    assertFalse(RecipientId.serializedListContains("", RecipientId.from(456)));
  }

  @Test
  public void serializedListContains_single_list_does_not_contain_item() {
    assertFalse(RecipientId.serializedListContains("123", RecipientId.from(456)));
  }

  @Test
  public void serializedListContains_single_list_does_contain_item() {
    assertTrue(RecipientId.serializedListContains("456", RecipientId.from(456)));
  }

  @Test
  public void serializedListContains_double_list_does_contain_item_in_first_position() {
    assertTrue(RecipientId.serializedListContains("456,123", RecipientId.from(456)));
  }

  @Test
  public void serializedListContains_double_list_does_contain_item_in_second_position() {
    assertTrue(RecipientId.serializedListContains("123,456", RecipientId.from(456)));
  }

  @Test
  public void serializedListContains_single_list_does_not_contain_item_due_to_extra_digit_at_start() {
    assertFalse(RecipientId.serializedListContains("1456", RecipientId.from(456)));
  }

  @Test
  public void serializedListContains_single_list_does_not_contain_item_due_to_extra_digit_at_end() {
    assertFalse(RecipientId.serializedListContains("4561", RecipientId.from(456)));
  }

  @Test
  public void serializedListContains_find_all_items_in_triple_list() {
    assertTrue(RecipientId.serializedListContains("11,12,13", RecipientId.from(11)));
    assertTrue(RecipientId.serializedListContains("11,12,13", RecipientId.from(12)));
    assertTrue(RecipientId.serializedListContains("11,12,13", RecipientId.from(13)));
  }

  @Test
  public void serializedListContains_cant_find_similar_items_in_triple_list() {
    assertFalse(RecipientId.serializedListContains("11,12,13", RecipientId.from(1)));
    assertFalse(RecipientId.serializedListContains("11,12,13", RecipientId.from(2)));
    assertFalse(RecipientId.serializedListContains("11,12,13", RecipientId.from(3)));
  }
}
