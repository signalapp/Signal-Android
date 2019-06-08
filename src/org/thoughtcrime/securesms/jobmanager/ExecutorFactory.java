package org.thoughtcrime.securesms.jobmanager;

import android.support.annotation.NonNull;

import java.util.concurrent.ExecutorService;

public interface ExecutorFactory {
  @NonNull ExecutorService newSingleThreadExecutor(@NonNull String name);
}
