package org.thoughtcrime.securesms.lock.v2;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

interface BaseKbsPinViewModel {
  LiveData<KbsPin> getUserEntry();

  LiveData<KbsKeyboardType> getKeyboard();

  @MainThread
  void setUserEntry(String userEntry);

  @MainThread
  void toggleAlphaNumeric();

  @MainThread
  void confirm();
}
