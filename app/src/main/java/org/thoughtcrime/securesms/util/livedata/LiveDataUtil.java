package org.thoughtcrime.securesms.util.livedata;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.function.Predicate;

import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.guava.Function;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

public final class LiveDataUtil {

  private LiveDataUtil() {
  }

  public static @NonNull <A> LiveData<A> filterNotNull(@NonNull LiveData<A> source) {
    //noinspection Convert2MethodRef
    return filter(source, a -> a != null);
  }

  /**
   * Filters output of a given live data based off a predicate.
   */
  public static @NonNull <A> LiveData<A> filter(@NonNull LiveData<A> source, @NonNull Predicate<A> predicate) {
    MediatorLiveData<A> mediator = new MediatorLiveData<>();

    mediator.addSource(source, newValue -> {
      if (predicate.test(newValue)) {
        mediator.setValue(newValue);
      }
    });

    return mediator;
  }

  /**
   * Runs the {@param backgroundFunction} on {@link SignalExecutors#BOUNDED}.
   * <p>
   * The background function order is run serially, albeit possibly across multiple threads.
   * <p>
   * The background function may not run for all {@param source} updates. Later updates taking priority.
   */
  public static <A, B> LiveData<B> mapAsync(@NonNull LiveData<A> source, @NonNull Function<A, B> backgroundFunction) {
    return mapAsync(SignalExecutors.BOUNDED, source, backgroundFunction);
  }

  /**
   * Runs the {@param backgroundFunction} on the supplied {@param executor}.
   * <p>
   * Regardless of the executor supplied, the background function is run serially.
   * <p>
   * The background function may not run for all {@param source} updates. Later updates taking priority.
   */
  public static <A, B> LiveData<B> mapAsync(@NonNull Executor executor, @NonNull LiveData<A> source, @NonNull Function<A, B> backgroundFunction) {
    MediatorLiveData<B> outputLiveData   = new MediatorLiveData<>();
    Executor            liveDataExecutor = new SerialMonoLifoExecutor(executor);

    outputLiveData.addSource(source, currentValue -> liveDataExecutor.execute(() -> outputLiveData.postValue(backgroundFunction.apply(currentValue))));

    return outputLiveData;
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

  /**
   * Merges the supplied live data streams.
   */
  public static <T> LiveData<T> merge(@NonNull List<LiveData<T>> liveDataList) {
    Set<LiveData<T>> set = new LinkedHashSet<>(liveDataList);

    set.addAll(liveDataList);

    if (set.size() == 1) {
      return liveDataList.get(0);
    }

    MediatorLiveData<T> mergedLiveData = new MediatorLiveData<>();

    for (LiveData<T> liveDataSource : set) {
      mergedLiveData.addSource(liveDataSource, mergedLiveData::setValue);
    }

    return mergedLiveData;
  }

  /**
   * @return Live data with just the initial value.
   */
  public static <T> LiveData<T> just(@NonNull T item) {
    return new MutableLiveData<>(item);
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
