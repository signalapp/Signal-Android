package org.signal.glide;

import androidx.annotation.NonNull;

public final class SignalGlideCodecs {

  private static Log.Provider logProvider = Log.Provider.EMPTY;

  private SignalGlideCodecs() {}

  public static void setLogProvider(@NonNull Log.Provider provider) {
    logProvider = provider;
  }

  public static @NonNull Log.Provider getLogProvider() {
    return logProvider;
  }
}
