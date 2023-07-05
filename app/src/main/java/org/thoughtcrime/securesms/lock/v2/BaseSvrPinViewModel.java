package org.thoughtcrime.securesms.lock.v2;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;

interface BaseSvrPinViewModel {
  LiveData<SvrPin> getUserEntry();

  LiveData<PinKeyboardType> getKeyboard();

  @MainThread
  void setUserEntry(String userEntry);

  @MainThread
  void toggleAlphaNumeric();

  @MainThread
  void confirm();
}
