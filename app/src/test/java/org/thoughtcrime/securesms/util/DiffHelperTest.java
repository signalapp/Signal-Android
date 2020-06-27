package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DiffHelperTest {

  private static final Object A = new Object();
  private static final Object B = new Object();
  private static final Object C = new Object();
  private static final Object D = new Object();

  @Test
  public void calculate_allRemoved() {
    DiffHelper.Result result = DiffHelper.calculate(Arrays.asList(A, B), Collections.emptyList());

    assertContentsEqual(Collections.emptyList(), result.getInserted());
    assertContentsEqual(Arrays.asList(A, B), result.getRemoved());
  }

  @Test
  public void calculate_allInserted() {
    DiffHelper.Result result = DiffHelper.calculate(Collections.emptyList(), Arrays.asList(A, B));

    assertContentsEqual(Arrays.asList(A, B), result.getInserted());
    assertContentsEqual(Collections.emptyList(), result.getRemoved());
  }

  @Test
  public void calculate_completeSwap() {
    DiffHelper.Result result = DiffHelper.calculate(Collections.singleton(A), Collections.singleton(B));

    assertContentsEqual(Collections.singleton(B), result.getInserted());
    assertContentsEqual(Collections.singleton(A), result.getRemoved());
  }

  @Test
  public void calculate_bothEmpty() {
    DiffHelper.Result result = DiffHelper.calculate(Collections.emptyList(), Collections.emptyList());

    assertContentsEqual(Collections.emptyList(), result.getInserted());
    assertContentsEqual(Collections.emptyList(), result.getRemoved());
  }

  private void assertContentsEqual(@NonNull Collection expected, @NonNull Collection actual) {
    assertEquals(expected.size(), actual.size());
    assertTrue(expected.containsAll(actual));
  }
}
