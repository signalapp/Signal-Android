package org.thoughtcrime.securesms.tracing;

import androidx.annotation.NonNull;

/**
 * Dummy implementation.
 */
final class TracerImpl implements Tracer {

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void start(@NonNull String methodName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void start(@NonNull String methodName, @NonNull String key, @NonNull String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void end(@NonNull String methodName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull byte[] serialize() {
    throw new UnsupportedOperationException();
  }
}
