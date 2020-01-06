package org.thoughtcrime.securesms.util;

/**
 * A function which takes 3 inputs and returns 1 output.
 */
public interface Function3<A, B, C, D> {
  D apply(A a, B b, C c);
}
