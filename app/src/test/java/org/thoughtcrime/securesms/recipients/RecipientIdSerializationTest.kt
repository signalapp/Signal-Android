package org.thoughtcrime.securesms.recipients

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.single
import org.junit.Test

class RecipientIdSerializationTest {
  @Test
  fun toSerializedList_empty() {
    assertThat(RecipientId.toSerializedList(emptyList())).isEmpty()
  }

  @Test
  fun toSerializedList_one_item() {
    assertThat(RecipientId.toSerializedList(listOf(RecipientId.from(123)))).isEqualTo("123")
  }

  @Test
  fun toSerializedList_two_items() {
    val ids = listOf(RecipientId.from(123), RecipientId.from("987"))
    val serializedList = RecipientId.toSerializedList(ids)
    assertThat(serializedList).isEqualTo("123,987")
  }

  @Test
  fun fromSerializedList_empty() {
    assertThat(RecipientId.fromSerializedList("")).isEmpty()
  }

  @Test
  fun fromSerializedList_one_item() {
    assertThat(RecipientId.fromSerializedList("123"))
      .single()
      .isEqualTo(RecipientId.from(123))
  }

  @Test
  fun fromSerializedList_two_items() {
    assertThat(RecipientId.fromSerializedList("123,456"))
      .containsExactly(RecipientId.from(123), RecipientId.from(456))
  }

  @Test
  fun fromSerializedList_recipient_serialize() {
    val recipientIds = RecipientId.fromSerializedList(RecipientId.from(123).serialize())
    assertThat(recipientIds)
      .single()
      .isEqualTo(RecipientId.from(123))
  }

  @Test
  fun serializedListContains_empty_list_does_not_contain_item() {
    assertThat(RecipientId.serializedListContains("", RecipientId.from(456))).isFalse()
  }

  @Test
  fun serializedListContains_single_list_does_not_contain_item() {
    assertThat(RecipientId.serializedListContains("123", RecipientId.from(456))).isFalse()
  }

  @Test
  fun serializedListContains_single_list_does_contain_item() {
    assertThat(RecipientId.serializedListContains("456", RecipientId.from(456))).isTrue()
  }

  @Test
  fun serializedListContains_double_list_does_contain_item_in_first_position() {
    assertThat(RecipientId.serializedListContains("456,123", RecipientId.from(456))).isTrue()
  }

  @Test
  fun serializedListContains_double_list_does_contain_item_in_second_position() {
    assertThat(RecipientId.serializedListContains("123,456", RecipientId.from(456))).isTrue()
  }

  @Test
  fun serializedListContains_single_list_does_not_contain_item_due_to_extra_digit_at_start() {
    assertThat(RecipientId.serializedListContains("1456", RecipientId.from(456))).isFalse()
  }

  @Test
  fun serializedListContains_single_list_does_not_contain_item_due_to_extra_digit_at_end() {
    assertThat(RecipientId.serializedListContains("4561", RecipientId.from(456))).isFalse()
  }

  @Test
  fun serializedListContains_find_all_items_in_triple_list() {
    assertThat(RecipientId.serializedListContains("11,12,13", RecipientId.from(11))).isTrue()
    assertThat(RecipientId.serializedListContains("11,12,13", RecipientId.from(12))).isTrue()
    assertThat(RecipientId.serializedListContains("11,12,13", RecipientId.from(13))).isTrue()
  }

  @Test
  fun serializedListContains_cant_find_similar_items_in_triple_list() {
    assertThat(RecipientId.serializedListContains("11,12,13", RecipientId.from(1))).isFalse()
    assertThat(RecipientId.serializedListContains("11,12,13", RecipientId.from(2))).isFalse()
    assertThat(RecipientId.serializedListContains("11,12,13", RecipientId.from(3))).isFalse()
  }
}
