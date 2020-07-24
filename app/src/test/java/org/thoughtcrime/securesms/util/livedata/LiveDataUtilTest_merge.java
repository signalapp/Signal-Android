package org.thoughtcrime.securesms.util.livedata;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.thoughtcrime.securesms.util.livedata.LiveDataTestUtil.assertNoValue;
import static org.thoughtcrime.securesms.util.livedata.LiveDataTestUtil.observeAndGetOneValue;

public final class LiveDataUtilTest_merge {

  @Rule
  public TestRule rule = new LiveDataRule();

  @Test
  public void merge_nothing() {
    LiveData<String> combined = LiveDataUtil.merge(Collections.emptyList());

    assertNoValue(combined);
  }
  
  @Test
  public void merge_one_is_a_no_op() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    
    LiveData<String> combined = LiveDataUtil.merge(Collections.singletonList(liveDataA));

    assertSame(liveDataA, combined);
  }

  @Test
  public void initially_no_value() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.merge(Arrays.asList(liveDataA, liveDataB));

    assertNoValue(combined);
  }

  @Test
  public void value_on_first() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.merge(Arrays.asList(liveDataA, liveDataB));

    liveDataA.setValue("A");

    assertEquals("A", observeAndGetOneValue(combined));
  }

  @Test
  public void value_on_second() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.merge(Arrays.asList(liveDataA, liveDataB));

    liveDataB.setValue("B");

    assertEquals("B", observeAndGetOneValue(combined));
  }

  @Test
  public void value_on_third() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();
    MutableLiveData<String> liveDataC = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.merge(Arrays.asList(liveDataA, liveDataB, liveDataC));

    liveDataC.setValue("C");

    assertEquals("C", observeAndGetOneValue(combined));
  }

  @Test
  public void several_values_merged() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();
    MutableLiveData<String> liveDataC = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.merge(Arrays.asList(liveDataA, liveDataB, liveDataC));

    liveDataC.setValue("C");

    assertEquals("C", observeAndGetOneValue(combined));

    liveDataA.setValue("A");

    assertEquals("A", observeAndGetOneValue(combined));

    liveDataB.setValue("B");

    assertEquals("B", observeAndGetOneValue(combined));
  }

  @Test
  public void combined_same_instance() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.merge(Arrays.asList(liveDataA, liveDataA));

    liveDataA.setValue("Echo! ");

    assertSame(liveDataA, combined);
  }

  @Test
  public void combined_same_instances_repeated() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();
    MutableLiveData<String> liveDataC = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.merge(Arrays.asList(liveDataA, liveDataB, liveDataC, liveDataA, liveDataB, liveDataC));

    liveDataC.setValue("C");

    assertEquals("C", observeAndGetOneValue(combined));

    liveDataA.setValue("A");

    assertEquals("A", observeAndGetOneValue(combined));

    liveDataB.setValue("B");

    assertEquals("B", observeAndGetOneValue(combined));
  }

  @Test
  public void on_a_set_before_combine() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();

    liveDataA.setValue("A");

    LiveData<String> combined = LiveDataUtil.merge(Arrays.asList(liveDataA, liveDataB));

    assertEquals("A", observeAndGetOneValue(combined));
  }

  @Test
  public void on_default_values() {
    MutableLiveData<Integer> liveDataA = new DefaultValueLiveData<>(10);
    MutableLiveData<Integer> liveDataB = new DefaultValueLiveData<>(30);

    LiveData<Integer> combined = LiveDataUtil.merge(Arrays.asList(liveDataA, liveDataB));

    assertEquals(Integer.valueOf(30), observeAndGetOneValue(combined));
  }
}
