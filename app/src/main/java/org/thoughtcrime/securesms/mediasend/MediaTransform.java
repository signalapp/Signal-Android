package org.thoughtcrime.securesms.mediasend;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

public interface MediaTransform {

  @WorkerThread
  @NonNull Media transform(@NonNull Context context, @NonNull Media media);
}
