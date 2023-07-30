package org.thoughtcrime.securesms.util.livedata;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import com.annimon.stream.function.Predicate;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;

import kotlin.jvm.functions.Function1;

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

    outputLiveData.addSource(source, currentValue -> {
      liveDataExecutor.execute(() -> {
        outputLiveData.postValue(backgroundFunction.apply(currentValue));
      });
    });

    return outputLiveData;
  }

  /**
   * Performs a map operation on the source observable and then only emits the mapped item if it has changed since the previous emission.
   */
  public static <A, B> LiveData<B> mapDistinct(@NonNull LiveData<A> source, @NonNull Function1<A, B> mapFunction) {
    return Transformations.distinctUntilChanged(Transformations.map(source, mapFunction));
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
   * Once there is non-null data on each input {@link LiveData}, the {@link Combine3} function is
   * run and produces a live data of the combined data.
   * <p>
   * As each live data changes, the combine function is re-run, and a new value is emitted always
   * with the latest, non-null values.
   */
  public static <A, B, C, R> LiveData<R> combineLatest(@NonNull LiveData<A> a,
                                                       @NonNull LiveData<B> b,
                                                       @NonNull LiveData<C> c,
                                                       @NonNull Combine3<A, B, C, R> combine) {
    return new Combine3LiveData<>(a, b, c, combine);
  }

  /**
   * Merges the supplied live data streams.
   */
  public static <T> LiveData<T> merge(@NonNull List<LiveData<T>> liveDataList) {
    Set<LiveData<T>> set = new LinkedHashSet<>(liveDataList.size());

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

  public static <A> LiveData<A> empty() {
    return new MutableLiveData<>();
  }

  /**
   * Emits {@param whileWaiting} until {@param main} starts emitting.
   */
  public static @NonNull <T> LiveData<T> until(@NonNull LiveData<T> main,
                                               @NonNull LiveData<T> whileWaiting)
  {
    MediatorLiveData<T> mediatorLiveData = new MediatorLiveData<>();

    mediatorLiveData.addSource(whileWaiting, mediatorLiveData::setValue);

    mediatorLiveData.addSource(main, value -> {
      mediatorLiveData.removeSource(whileWaiting);
      mediatorLiveData.setValue(value);
    });

    return mediatorLiveData;
  }

  /**
   * Skip the first {@param skip} emissions before emitting everything else.
   */
  public static @NonNull <T> LiveData<T> skip(@NonNull LiveData<T> source, int skip) {
    return new MediatorLiveData<T>() {
      int skipsRemaining = skip;

      {
        addSource(source, value -> {
          if (skipsRemaining <= 0) {
            setValue(value);
          } else {
            skipsRemaining--;
          }
        });
      }
    };
  }

  /**
   * After {@param delay} ms after observation, emits a single Object, {@param value}.
   */
  public static <T> LiveData<T> delay(long delay, T value) {
    return new MutableLiveData<T>() {
      boolean emittedValue;

      @Override
      protected void onActive() {
        if (emittedValue) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> setValue(value), delay);
        emittedValue = true;
      }
    };
  }

  public static <T> LiveData<T> never() {
    return new MutableLiveData<>();
  }

  public static <T, R> LiveData<T> distinctUntilChanged(@NonNull LiveData<T> source, @NonNull Function<T, R> selector) {
    return LiveDataUtil.distinctUntilChanged(source, (current, next) -> Objects.equals(selector.apply(current), selector.apply(next)));
  }

  public static <T> LiveData<T> distinctUntilChanged(@NonNull LiveData<T> source, @NonNull EqualityChecker<T> checker) {
    final MediatorLiveData<T> outputLiveData = new MediatorLiveData<>();
    outputLiveData.addSource(source, new Observer<T>() {

      boolean firstChange = true;

      @Override
      public void onChanged(T nextValue) {
        T currentValue = outputLiveData.getValue();

        if (currentValue == null && nextValue == null) {
          return;
        }

        if (firstChange          ||
            currentValue == null ||
            nextValue    == null ||
            !checker.contentsMatch(currentValue, nextValue))
        {
          firstChange = false;
          outputLiveData.setValue(nextValue);
        }
      }
    });

    return outputLiveData;
  }

  /**
   * Observes a source until the predicate is met. The final value matching the predicate is emitted.
   */
  public static @NonNull <A> LiveData<A> until(@NonNull LiveData<A> source, @NonNull Predicate<A> predicate) {
    MediatorLiveData<A> mediator = new MediatorLiveData<>();

    mediator.addSource(source, newValue -> {
      mediator.setValue(newValue);
      if (predicate.test(newValue)) {
        mediator.removeSource(source);
      }
    });

    return mediator;
  }

  public interface Combine<A, B, R> {
    @NonNull R apply(@NonNull A a, @NonNull B b);
  }

  public interface Combine3<A, B, C, R> {
    @NonNull R apply(@NonNull A a, @NonNull B b, @NonNull C c);
  }

  public interface EqualityChecker<T> {
    boolean contentsMatch(@NonNull T current, @NonNull T next);
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

  private static final class Combine3LiveData<A, B, C, R> extends MediatorLiveData<R> {
    private A a;
    private B b;
    private C c;

    Combine3LiveData(LiveData<A> liveDataA, LiveData<B> liveDataB, LiveData<C> liveDataC, Combine3<A, B, C, R> combine) {
      Preconditions.checkArgument(liveDataA != liveDataB && liveDataB != liveDataC && liveDataA != liveDataC);

      addSource(liveDataA, (a) -> {
        if (a != null) {
          this.a = a;
          if (b != null && c != null) {
            setValue(combine.apply(a, b, c));
          }
        }
      });

      addSource(liveDataB, (b) -> {
        if (b != null) {
          this.b = b;
          if (a != null && c != null) {
            setValue(combine.apply(a, b, c));
          }
        }
      });

      addSource(liveDataC, (c) -> {
        if (c != null) {
          this.c = c;
          if (a != null && b != null) {
            setValue(combine.apply(a, b, c));
          }
        }
      });
    }
  }
}
