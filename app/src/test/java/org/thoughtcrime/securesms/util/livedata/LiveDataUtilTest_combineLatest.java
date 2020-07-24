package org.thoughtcrime.securesms.util.livedata;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;

import static org.junit.Assert.assertEquals;
import static org.thoughtcrime.securesms.util.livedata.LiveDataTestUtil.assertNoValue;
import static org.thoughtcrime.securesms.util.livedata.LiveDataTestUtil.observeAndGetOneValue;

public final class LiveDataUtilTest_combineLatest {

  @Rule
  public TestRule rule = new LiveDataRule();

  @Test
  public void initially_no_value() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.combineLatest(liveDataA, liveDataB, (a, b) -> a + b);

    assertNoValue(combined);
  }

  @Test
  public void no_value_after_just_a() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.combineLatest(liveDataA, liveDataB, (a, b) -> a + b);

    liveDataA.setValue("Hello, ");

    assertNoValue(combined);
  }

  @Test
  public void no_value_after_just_b() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.combineLatest(liveDataA, liveDataB, (a, b) -> a + b);

    liveDataB.setValue("World!");

    assertNoValue(combined);
  }

  @Test
  public void combined_value_after_a_and_b() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.combineLatest(liveDataA, liveDataB, (a, b) -> a + b);

    liveDataA.setValue("Hello, ");
    liveDataB.setValue("World!");

    assertEquals("Hello, World!", observeAndGetOneValue(combined));
  }

  @Test
  public void on_update_a() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.combineLatest(liveDataA, liveDataB, (a, b) -> a + b);

    liveDataA.setValue("Hello, ");
    liveDataB.setValue("World!");

    assertEquals("Hello, World!", observeAndGetOneValue(combined));

    liveDataA.setValue("Welcome, ");
    assertEquals("Welcome, World!", observeAndGetOneValue(combined));
  }

  @Test
  public void on_update_b() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.combineLatest(liveDataA, liveDataB, (a, b) -> a + b);

    liveDataA.setValue("Hello, ");
    liveDataB.setValue("World!");

    assertEquals("Hello, World!", observeAndGetOneValue(combined));

    liveDataB.setValue("Joe!");
    assertEquals("Hello, Joe!", observeAndGetOneValue(combined));
  }

  @Test
  public void combined_same_instance() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();

    LiveData<String> combined = LiveDataUtil.combineLatest(liveDataA, liveDataA, (a, b) -> a + b);

    liveDataA.setValue("Echo! ");

    assertEquals("Echo! Echo! ", observeAndGetOneValue(combined));
  }

  @Test
  public void on_a_set_before_combine() {
    MutableLiveData<String> liveDataA = new MutableLiveData<>();
    MutableLiveData<String> liveDataB = new MutableLiveData<>();

    liveDataA.setValue("Hello, ");

    LiveData<String> combined = LiveDataUtil.combineLatest(liveDataA, liveDataB, (a, b) -> a + b);

    liveDataB.setValue("World!");

    assertEquals("Hello, World!", observeAndGetOneValue(combined));
  }

  @Test
  public void on_default_values() {
    MutableLiveData<Integer> liveDataA = new DefaultValueLiveData<>(10);
    MutableLiveData<Integer> liveDataB = new DefaultValueLiveData<>(30);

    LiveData<Integer> combined = LiveDataUtil.combineLatest(liveDataA, liveDataB, (a, b) -> a * b);

    assertEquals(Integer.valueOf(300), observeAndGetOneValue(combined));
  }
}
