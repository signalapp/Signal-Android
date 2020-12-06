package org.thoughtcrime.securesms.util.livedata;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.thoughtcrime.securesms.util.livedata.LiveDataTestUtil.assertNoValue;
import static org.thoughtcrime.securesms.util.livedata.LiveDataTestUtil.observeAndGetOneValue;

public final class LiveDataUtilTest_skip {

  @Rule
  public TestRule rule = new LiveDataRule();

  @Test
  public void skip_no_value() {
    MutableLiveData<String> liveData = new MutableLiveData<>();

    LiveData<String> skipped = LiveDataUtil.skip(liveData, 0);

    assertNoValue(skipped);
  }

  @Test
  public void skip_same_value_with_zero_skip() {
    MutableLiveData<String> liveData = new MutableLiveData<>();

    LiveData<String> skipped = LiveDataUtil.skip(liveData, 0);
    liveData.setValue("A");

    assertEquals("A", observeAndGetOneValue(skipped));
  }

  @Test
  public void skip_second_value_with_skip_one() {
    MutableLiveData<String> liveData     = new MutableLiveData<>();
    TestObserver<String>    testObserver = new TestObserver<>();

    LiveData<String> skipped = LiveDataUtil.skip(liveData, 1);

    skipped.observeForever(testObserver);
    liveData.setValue("A");
    liveData.setValue("B");
    skipped.removeObserver(testObserver);

    Assertions.assertThat(testObserver.getValues())
              .containsExactly("B");
  }

  @Test
  public void skip_no_value_with_skip() {
    MutableLiveData<String> liveData = new MutableLiveData<>();

    LiveData<String> skipped = LiveDataUtil.skip(liveData, 1);
    liveData.setValue("A");

    assertNoValue(skipped);
  }

  @Test
  public void skip_third_and_fourth_value_with_skip_two() {
    MutableLiveData<String> liveData     = new MutableLiveData<>();
    TestObserver<String>    testObserver = new TestObserver<>();

    LiveData<String> skipped = LiveDataUtil.skip(liveData, 2);

    skipped.observeForever(testObserver);
    liveData.setValue("A");
    liveData.setValue("B");
    liveData.setValue("C");
    liveData.setValue("D");
    skipped.removeObserver(testObserver);

    Assertions.assertThat(testObserver.getValues())
              .containsExactly("C", "D");
  }

  @Test
  public void skip_set_one_before_then_skip() {
    MutableLiveData<String> liveData     = new MutableLiveData<>();
    TestObserver<String>    testObserver = new TestObserver<>();

    liveData.setValue("A");

    LiveData<String> skipped = LiveDataUtil.skip(liveData, 2);

    skipped.observeForever(testObserver);
    liveData.setValue("B");
    liveData.setValue("C");
    liveData.setValue("D");
    skipped.removeObserver(testObserver);

    Assertions.assertThat(testObserver.getValues())
              .containsExactly("C", "D");
  }

  @Test
  public void skip_set_two_before_then_skip() {
    MutableLiveData<String> liveData     = new MutableLiveData<>();
    TestObserver<String>    testObserver = new TestObserver<>();

    liveData.setValue("A");
    liveData.setValue("B");

    LiveData<String> skipped = LiveDataUtil.skip(liveData, 2);

    skipped.observeForever(testObserver);
    liveData.setValue("C");
    liveData.setValue("D");
    skipped.removeObserver(testObserver);

    Assertions.assertThat(testObserver.getValues())
              .containsExactly("D");
  }
}
