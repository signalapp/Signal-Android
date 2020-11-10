package org.thoughtcrime.securesms.tracing;

import androidx.annotation.NonNull;

/**
 * A class to create Perfetto-compatible traces.
 */
public interface Tracer {

  TracerImpl INSTANCE = new TracerImpl();

  static @NonNull Tracer getInstance() {
    return INSTANCE;
  }

  /**
   * True if enabled, otherwise false.
   */
  boolean isEnabled();

  /**
   * Marks the start of a method call. Always follow this with a call to {@link #end(String)}.
   */
  void start(@NonNull String methodName);

  /**
   * Marks the start of a method call. Always follow this with a call to {@link #end(String)}.
   *
   * Includes the ability to pass a key-value pair that will be shown in the trace when you click
   * on the slice.
   */
  void start(@NonNull String methodName, @NonNull String key, @NonNull String value);

  /**
   * Marks the end of a method call.
   */
  void end(@NonNull String methodName);

  /**
   * Serializes the current state of the trace to a Perfetto-compatible byte array. Note that
   * there's no locking here, and therefore tracing will continue. We're just grabbing a best-effort
   * snapshot.
   */
  @NonNull byte[] serialize();
}
