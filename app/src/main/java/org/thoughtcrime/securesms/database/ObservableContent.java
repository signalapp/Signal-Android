package org.thoughtcrime.securesms.database;

import android.database.ContentObserver;
import androidx.annotation.NonNull;

import java.io.Closeable;

public interface ObservableContent extends Closeable {
  void registerContentObserver(@NonNull ContentObserver observer);
  void unregisterContentObserver(@NonNull ContentObserver observer);
}
