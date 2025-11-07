package org.thoughtcrime.securesms.util.livedata;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import kotlin.Pair;

public final class LiveDataPair<A, B> extends MediatorLiveData<Pair<A, B>> {
  private A a;
  private B b;

  public LiveDataPair(@NonNull LiveData<A> liveDataA,
                      @NonNull LiveData<B> liveDataB)
  {
    this(liveDataA, liveDataB, null, null);
  }

  public LiveDataPair(@NonNull LiveData<A> liveDataA,
                      @NonNull LiveData<B> liveDataB,
                      @Nullable A initialA,
                      @Nullable B initialB)
  {
    a = initialA;
    b = initialB;
    setValue(new Pair<>(a, b));

    if (liveDataA == liveDataB) {

      addSource(liveDataA, (a) -> {
        if (a != null) {
          this.a = a;
          //noinspection unchecked: A is B if live datas are same instance
          this.b = (B) a;
        }
        setValue(new Pair<>(a, b));
      });

    } else {

      addSource(liveDataA, (a) -> {
        if (a != null) {
          this.a = a;
        }
        setValue(new Pair<>(a, b));
      });

      addSource(liveDataB, (b) -> {
        if (b != null) {
          this.b = b;
        }
        setValue(new Pair<>(a, b));
      });
    }
  }
}
