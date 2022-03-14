package org.thoughtcrime.securesms.payments.reconciliation;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.thoughtcrime.securesms.payments.reconciliation.ZipList.zipList;
import static java.util.Collections.emptyList;

public final class ZipListTest {

  @Test
  public void empty_list_zip() {
    List<Long> a = emptyList();
    List<Long> b = emptyList();
    assertEquals(emptyList(), zipList(a, b, Long::compare));
  }

  @Test
  public void empty_list_rhs_zip() {
    List<Long> a = Arrays.asList(1L, 2L, 3L);
    List<Long> b = emptyList();
    assertEquals(a, zipList(a, b, Long::compare));
  }

  @Test
  public void empty_list_lhs_zip() {
    List<Long> a = emptyList();
    List<Long> b = Arrays.asList(1L, 2L, 3L);
    assertEquals(b, zipList(a, b, Long::compare));
  }

  @Test
  public void two_lists_no_overlap() {
    List<Integer> a = Arrays.asList(1, 2, 3);
    List<Integer> b = Arrays.asList(4, 5, 6);
    assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), zipList(a, b, Integer::compare));
  }

  @Test
  public void two_lists_overlap() {
    List<Integer> a = Arrays.asList(1, 2, 4);
    List<Integer> b = Arrays.asList(3, 5, 6);
    assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), zipList(a, b, Integer::compare));
  }

  @Test
  public void two_lists_overlap_reversed() {
    List<Integer> a = Arrays.asList(3, 5, 6);
    List<Integer> b = Arrays.asList(1, 2, 4);
    assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), zipList(a, b, Integer::compare));
  }

  @Test
  public void two_lists_with_out_of_order_items() {
    List<Integer> a = Arrays.asList(3, 0, 4);
    List<Integer> b = Arrays.asList(1, 0, 2);
    assertEquals(Arrays.asList(1, 0, 2, 3, 0, 4), zipList(a, b, Integer::compare));
  }

  @Test
  public void two_lists_with_out_of_order_items_and_overlap() {
    List<Integer> a = Arrays.asList(3, -2, 5);
    List<Integer> b = Arrays.asList(1, -1, 4);
    assertEquals(Arrays.asList(1, -1, 3, -2, 4, 5), zipList(a, b, Integer::compare));
  }
}
