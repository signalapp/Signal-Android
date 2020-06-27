package org.thoughtcrime.securesms.util;

import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;

public class Triple<A, B, C> {

  private final A a;
  private final B b;
  private final C c;

  public Triple(@Nullable A a, @Nullable B b, @Nullable C c) {
    this.a = a;
    this.b = b;
    this.c = c;
  }

  public @Nullable A first() {
    return a;
  }

  public @Nullable B second() {
    return b;
  }

  public @Nullable C third() {
    return c;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Triple)) {
      return false;
    }
    Triple<?, ?, ?> t = (Triple<?, ?, ?>) o;
    return ObjectsCompat.equals(t.a, a) && ObjectsCompat.equals(t.b, b) && ObjectsCompat.equals(t.c, c);
  }

  @Override
  public int hashCode() {
    return (a == null ? 0 : a.hashCode()) ^ (b == null ? 0 : b.hashCode()) ^ (c == null ? 0 : c.hashCode());
  }
}
