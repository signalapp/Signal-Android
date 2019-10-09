package org.thoughtcrime.securesms.util;

import java.io.IOException;

/**
 * A function which takes 1 input and returns 1 output, and is capable of throwing an IO Exception.
 */
public interface IOFunction<I, O> {
  O apply(I input) throws IOException;
}
