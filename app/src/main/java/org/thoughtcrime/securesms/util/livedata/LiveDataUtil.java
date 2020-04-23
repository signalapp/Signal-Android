package org.thoughtcrime.securesms.util.livedata;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

public final class LiveDataUtil {

  private LiveDataUtil() {
  }

  /**
   * Once there is non-null data on both input {@link LiveData}, the {@link Combine} function is run
   * and produces a live data of the combined data.
   * <p>
   * As each live data changes, the combine function is re-run, and a new value is emitted always
   * with the latest, non-null values.
   */
  public static <A, B, R> LiveData<R> combineLatest(@NonNull LiveData<A> a,
                                                    @NonNull LiveData<B> b,
                                                    @NonNull Combine<A, B, R> combine) {
    return new CombineLiveData<>(a, b, combine);
  }

  public interface Combine<A, B, R> {
    @NonNull R apply(@NonNull A a, @NonNull B b);
  }

  private static final class CombineLiveData<A, B, R> extends MediatorLiveData<R> {
    private A a;
    private B b;

    CombineLiveData(LiveData<A> liveDataA, LiveData<B> liveDataB, Combine<A, B, R> combine) {
      if (liveDataA == liveDataB) {

        addSource(liveDataA, (a) -> {
          if (a != null) {
            this.a = a;
            //noinspection unchecked: A is B if live datas are same instance
            this.b = (B) a;
            setValue(combine.apply(a, b));
          }
        });

      } else {

        addSource(liveDataA, (a) -> {
          if (a != null) {
            this.a = a;
            if (b != null) {
              setValue(combine.apply(a, b));
            }
          }
        });

        addSource(liveDataB, (b) -> {
          if (b != null) {
            this.b = b;
            if (a != null) {
              setValue(combine.apply(a, b));
            }
          }
        });
      }
    }
  }
}
