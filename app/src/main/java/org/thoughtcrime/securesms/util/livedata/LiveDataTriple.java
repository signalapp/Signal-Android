package org.thoughtcrime.securesms.util.livedata;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import org.thoughtcrime.securesms.util.Triple;

public final class LiveDataTriple<A, B, C> extends MediatorLiveData<Triple<A, B, C>> {
  private A a;
  private B b;
  private C c;

  public LiveDataTriple(@NonNull LiveData<A> liveDataA,
                        @NonNull LiveData<B> liveDataB,
                        @NonNull LiveData<C> liveDataC)
  {
    this(liveDataA, liveDataB, liveDataC, null, null, null);
  }

  public LiveDataTriple(@NonNull LiveData<A> liveDataA,
                        @NonNull LiveData<B> liveDataB,
                        @NonNull LiveData<C> liveDataC,
                        @Nullable A initialA,
                        @Nullable B initialB,
                        @Nullable C initialC)
  {
    a = initialA;
    b = initialB;
    c = initialC;
    setValue(new Triple<>(a, b, c));

    if (liveDataA == liveDataB && liveDataA == liveDataC) {

      addSource(liveDataA, a -> {
        if (a != null) {
          this.a = a;

          //noinspection unchecked: A is B if live datas are same instance
          this.b = (B) a;

          //noinspection unchecked: A is C if live datas are same instance
          this.c = (C) a;
        }

        setValue(new Triple<>(a, b, c));
      });

    } else if (liveDataA == liveDataB) {

      addSource(liveDataA, a -> {
        if (a != null) {
          this.a = a;

          //noinspection unchecked: A is B if live datas are same instance
          this.b = (B) a;
        }

        setValue(new Triple<>(a, b, c));
      });

      addSource(liveDataC, c -> {
        if (c != null) {
          this.c = c;
        }
        setValue(new Triple<>(a, b, c));
      });

    } else if (liveDataA == liveDataC) {

      addSource(liveDataA, a -> {
        if (a != null) {
          this.a = a;

          //noinspection unchecked: A is C if live datas are same instance
          this.c = (C) a;
        }

        setValue(new Triple<>(a, b, c));
      });

      addSource(liveDataB, b -> {
        if (b != null) {
          this.b = b;
        }
        setValue(new Triple<>(a, b, c));
      });

    } else if (liveDataB == liveDataC) {

      addSource(liveDataB, b -> {
        if (b != null) {
          this.b = b;

          //noinspection unchecked: A is C if live datas are same instance
          this.c = (C) b;
        }

        setValue(new Triple<>(a, b, c));
      });

      addSource(liveDataA, a -> {
        if (a != null) {
          this.a = a;
        }
        setValue(new Triple<>(a, b, c));
      });

    } else {

      addSource(liveDataA, a -> {
        if (a != null) {
          this.a = a;
        }
        setValue(new Triple<>(a, b, c));
      });

      addSource(liveDataB, b -> {
        if (b != null) {
          this.b = b;
        }
        setValue(new Triple<>(a, b, c));
      });

      addSource(liveDataC, c -> {
        if (c != null) {
          this.c = c;
        }
        setValue(new Triple<>(a, b, c));
      });

    }
  }
}
