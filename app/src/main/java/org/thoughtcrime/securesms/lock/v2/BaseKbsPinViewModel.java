package org.thoughtcrime.securesms.lock.v2;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;

interface BaseKbsPinViewModel {
  LiveData<KbsPin> getUserEntry();

  LiveData<PinKeyboardType> getKeyboard();

  @MainThread
  void setUserEntry(String userEntry);

  @MainThread
  void toggleAlphaNumeric();

  @MainThread
  void confirm();
}
