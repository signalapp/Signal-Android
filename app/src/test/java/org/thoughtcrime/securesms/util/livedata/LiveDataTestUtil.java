package org.thoughtcrime.securesms.util.livedata;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;

public final class LiveDataTestUtil {

  /**
   * Observes and then instantly un-observes the supplied live data.
   * <p>
   * This will therefore only work in conjunction with {@link LiveDataRule}.
   */
  public static <T> T observeAndGetOneValue(final LiveData<T> liveData) {
    AtomicReference<T> data     = new AtomicReference<>();
    Observer<T>        observer = data::set;

    liveData.observeForever(observer);
    liveData.removeObserver(observer);

    return data.get();
  }

  public static  <T> void assertNoValue(final LiveData<T> liveData) {
    AtomicReference<Boolean> data     = new AtomicReference<>(false);
    Observer<T>              observer = newValue -> data.set(true);

    liveData.observeForever(observer);
    liveData.removeObserver(observer);

    assertFalse("Expected no value", data.get());
  }
}
